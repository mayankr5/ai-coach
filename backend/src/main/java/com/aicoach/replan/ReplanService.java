package com.aicoach.replan;

import com.aicoach.common.NotFoundException;
import com.aicoach.goal.Goal;
import com.aicoach.goal.GoalRepository;
import com.aicoach.goal.Milestone;
import com.aicoach.plan.AiUnavailableException;
import com.aicoach.plan.PlanRevision;
import com.aicoach.plan.PlanRevisionRepository;
import com.aicoach.plan.RevisionSource;
import com.aicoach.replan.PlanChanges.TaskChange;
import com.aicoach.replan.RePlanRequest.MilestoneView;
import com.aicoach.replan.RePlanRequest.TaskView;
import com.aicoach.task.Task;
import com.aicoach.task.TaskRepository;
import com.aicoach.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adjusts a goal's upcoming tasks. Callers must already have checked ownership.
 * Like plan generation, the AI call runs between two short transactions.
 */
@Service
public class ReplanService {

    private static final Logger log = LoggerFactory.getLogger(ReplanService.class);
    static final int MAX_ATTEMPTS = 2;
    static final int COMPLETION_LOOKBACK_DAYS = 14;

    private final GoalRepository goals;
    private final TaskRepository tasks;
    private final PlanRevisionRepository revisions;
    private final RePlanner aiRePlanner;
    private final RuleBasedRePlanner ruleBased;
    private final ReplanValidator validator;
    private final TransactionTemplate tx;
    private final int taskWindowDays;

    public ReplanService(GoalRepository goals, TaskRepository tasks, PlanRevisionRepository revisions,
                         RePlanner aiRePlanner, RuleBasedRePlanner ruleBased, ReplanValidator validator,
                         TransactionTemplate tx, @Value("${app.planning.task-window-days}") int taskWindowDays) {
        this.goals = goals;
        this.tasks = tasks;
        this.revisions = revisions;
        this.aiRePlanner = aiRePlanner;
        this.ruleBased = ruleBased;
        this.validator = validator;
        this.tx = tx;
        this.taskWindowDays = taskWindowDays;
    }

    public record ReplanResult(String coachMessage, RevisionSource source) {
    }

    /**
     * @param from  first date the re-plan may change
     * @param useAi false for the rule-based planner only (nightly job: no AI cost per user)
     */
    public ReplanResult replan(Long goalId, LocalDate from, String note, boolean useAi) {
        RePlanRequest request = tx.execute(status -> buildRequest(findGoal(goalId), from, note));

        PlanChanges changes = null;
        RevisionSource source = useAi ? RevisionSource.AI : RevisionSource.NIGHTLY;
        if (useAi) {
            changes = tryAi(request);
            if (changes == null) {
                source = RevisionSource.FALLBACK;
            }
        }
        if (changes == null) {
            changes = ruleBased.replan(request);
        }

        PlanChanges finalChanges = changes;
        RevisionSource finalSource = source;
        tx.executeWithoutResult(status -> apply(findGoal(goalId), request, finalChanges, finalSource));
        return new ReplanResult(changes.coachMessage().strip(), source);
    }

    private PlanChanges tryAi(RePlanRequest request) {
        List<String> errors = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            PlanChanges proposed;
            try {
                proposed = aiRePlanner.replan(request, errors);
            } catch (AiUnavailableException e) {
                log.warn("AI re-plan unavailable for goal {}, using fallback: {}", request.goalId(), e.getMessage());
                return null;
            }
            errors = validator.validate(request, proposed);
            if (errors.isEmpty()) {
                return proposed;
            }
            log.warn("Re-plan attempt {} for goal {} rejected: {}", attempt, request.goalId(), errors);
        }
        return null;
    }

    private RePlanRequest buildRequest(Goal goal, LocalDate from, String note) {
        LocalDate windowEnd = from.plusDays(taskWindowDays - 1);
        if (windowEnd.isAfter(goal.getDeadline())) {
            windowEnd = goal.getDeadline();
        }
        List<TaskView> upcoming = tasks.findUpcomingPending(goal.getId(), from).stream().map(ReplanService::view).toList();
        List<TaskView> missed = tasks.findUncarriedMissed(goal.getId(), from).stream().map(ReplanService::view).toList();
        List<MilestoneView> milestones = goal.getMilestones().stream()
                .map(m -> new MilestoneView(m.getId(), m.getTitle(), m.getTargetDate())).toList();

        return new RePlanRequest(goal.getId(), goal.getTitle(), goal.getDescription(), goal.getExperienceLevel(),
                goal.getMinutesPerDay(), goal.getDeadline(), from, windowEnd,
                completionRate(goal.getId(), from), note, upcoming, missed, milestones);
    }

    /** (done minutes + half of partial minutes) / all minutes reported over the lookback period. */
    private Double completionRate(Long goalId, LocalDate from) {
        List<Task> recent = tasks.findByGoalIdAndScheduledDateBetween(goalId,
                from.minusDays(COMPLETION_LOOKBACK_DAYS), from.minusDays(1));
        double total = 0;
        double done = 0;
        for (Task t : recent) {
            if (t.getStatus() == TaskStatus.PENDING) continue;
            total += t.getEstimatedMinutes();
            if (t.getStatus() == TaskStatus.DONE) done += t.getEstimatedMinutes();
            if (t.getStatus() == TaskStatus.PARTIAL) done += t.getEstimatedMinutes() / 2.0;
        }
        return total == 0 ? null : done / total;
    }

    private void apply(Goal goal, RePlanRequest request, PlanChanges changes, RevisionSource source) {
        // Only tasks still upcoming and pending may change; anything the user touched meanwhile is left alone
        Map<Long, Task> editable = tasks.findUpcomingPending(goal.getId(), request.from()).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
        Map<Long, Milestone> milestones = goal.getMilestones().stream()
                .collect(Collectors.toMap(Milestone::getId, Function.identity()));

        for (TaskChange c : changes.changes() == null ? List.<TaskChange>of() : changes.changes()) {
            switch (c.action()) {
                case MOVE -> {
                    Task t = editable.get(c.taskId());
                    if (t != null) t.setScheduledDate(c.date());
                }
                case RESIZE -> {
                    Task t = editable.get(c.taskId());
                    if (t != null) t.setEstimatedMinutes(c.minutes());
                }
                case REMOVE -> {
                    Task t = editable.remove(c.taskId());
                    if (t != null) tasks.delete(t);
                }
                case ADD -> {
                    Task t = new Task();
                    t.setGoal(goal);
                    t.setMilestone(c.milestoneId() == null ? null : milestones.get(c.milestoneId()));
                    t.setTitle(c.title().strip());
                    t.setScheduledDate(c.date());
                    t.setEstimatedMinutes(c.minutes());
                    tasks.save(t);
                }
            }
        }

        List<Long> missedIds = request.missed().stream().map(TaskView::id).toList();
        if (!missedIds.isEmpty()) {
            tasks.findByGoalIdAndIdIn(goal.getId(), missedIds).forEach(t -> t.setCarriedOver(true));
        }

        PlanRevision revision = new PlanRevision();
        revision.setGoal(goal);
        revision.setSource(source);
        revision.setSummary(changes.coachMessage().strip());
        revisions.save(revision);
    }

    private Goal findGoal(Long goalId) {
        return goals.findById(goalId).orElseThrow(() -> new NotFoundException("Goal not found"));
    }

    private static TaskView view(Task t) {
        return new TaskView(t.getId(), t.getTitle(), t.getScheduledDate(), t.getEstimatedMinutes(),
                t.getMilestone() == null ? null : t.getMilestone().getId(), t.getStatus());
    }
}
