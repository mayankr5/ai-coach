package com.aicoach.replan;

import com.aicoach.ai.AiResult;
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
import com.aicoach.task.Completion;
import com.aicoach.task.Task;
import com.aicoach.task.TaskRepository;
import com.aicoach.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adjusts a goal's upcoming tasks. Callers must already have checked ownership.
 * Code does all scheduling ({@link CatchUpScheduler}); the AI only writes the coach message, so a
 * failing or weak model never changes what gets scheduled. The AI call runs between two short transactions.
 */
@Service
public class ReplanService {

    private static final Logger log = LoggerFactory.getLogger(ReplanService.class);
    static final int COMPLETION_LOOKBACK_DAYS = 14;

    private final GoalRepository goals;
    private final TaskRepository tasks;
    private final PlanRevisionRepository revisions;
    private final CatchUpScheduler scheduler;
    private final CoachWriter coachWriter;
    private final TransactionTemplate tx;
    private final int taskWindowDays;

    public ReplanService(GoalRepository goals, TaskRepository tasks, PlanRevisionRepository revisions,
                         CatchUpScheduler scheduler, CoachWriter coachWriter, TransactionTemplate tx,
                         @Value("${app.planning.task-window-days}") int taskWindowDays) {
        this.goals = goals;
        this.tasks = tasks;
        this.revisions = revisions;
        this.scheduler = scheduler;
        this.coachWriter = coachWriter;
        this.tx = tx;
        this.taskWindowDays = taskWindowDays;
    }

    public record ReplanResult(String coachMessage, RevisionSource source) {
    }

    /**
     * @param from  first date the re-plan may change
     * @param useAi false for the fixed message only (nightly job: no AI cost per user)
     */
    public ReplanResult replan(Long goalId, LocalDate from, String note, boolean useAi) {
        RePlanRequest request = tx.execute(status -> buildRequest(findGoal(goalId), from, note));
        CatchUpPlan plan = scheduler.schedule(request);

        String message = null;
        String model = null;
        RevisionSource source = useAi ? RevisionSource.AI : RevisionSource.NIGHTLY;
        if (useAi) {
            try {
                AiResult<String> written = coachWriter.write(request, plan);
                message = written.value();
                model = written.model();
            } catch (AiUnavailableException e) {
                log.warn("AI coach message unavailable for goal {}, using default: {}", goalId, e.getMessage());
                source = RevisionSource.FALLBACK;
            }
        }
        if (message == null) {
            message = defaultMessage(plan, request.today());
        }

        PlanChanges changes = new PlanChanges(message, plan.changes());
        RevisionSource finalSource = source;
        String finalModel = model;
        tx.executeWithoutResult(status -> apply(findGoal(goalId), request, changes, finalSource, finalModel));
        return new ReplanResult(message, source);
    }

    static String defaultMessage(CatchUpPlan plan, LocalDate today) {
        DayLabels labels = new DayLabels(today);
        if (plan.isEmpty()) {
            return "Nothing to catch up on. Keep going with the plan.";
        }
        List<String> parts = new ArrayList<>();
        int added = plan.followUps().size();
        if (added > 0) {
            parts.add("Added " + added + (added == 1 ? " catch-up task" : " catch-up tasks") + " starting "
                    + labels.of(plan.followUps().getFirst().date()) + ".");
        }
        if (plan.shifted() > 0) {
            parts.add("Moved " + plan.shifted() + (plan.shifted() == 1 ? " later task" : " later tasks")
                    + " forward to keep the order.");
        }
        if (!plan.extraDays().isEmpty()) {
            parts.add("Using your extra time on " + labels.of(plan.extraDays()) + ".");
        }
        if (!plan.bufferDaysUsed().isEmpty()) {
            parts.add("Using your buffer day on " + labels.of(plan.bufferDaysUsed()) + " to catch up.");
        }
        if (plan.overflow() > 0) {
            parts.add(plan.overflow() + (plan.overflow() == 1 ? " task doesn't" : " tasks don't")
                    + " fit before the deadline; consider extending it or adding daily time.");
        }
        return String.join(" ", parts);
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
                goal.getMinutesPerDay(), goal.getExtraMinutesPerDay(), goal.getBufferDay(), goal.getDeadline(),
                from, windowEnd,
                completionRate(goal.getId(), from), note, recentExtraDays(goal, from), upcoming, missed, milestones);
    }

    /** Days just before {@code from} whose planned tasks add up to more than the normal daily time. */
    private List<LocalDate> recentExtraDays(Goal goal, LocalDate from) {
        Map<LocalDate, Integer> planned = tasks.findByGoalIdAndScheduledDateBetween(goal.getId(),
                        from.minusDays(CatchUpScheduler.MAX_EXTRA_DAYS_IN_A_ROW), from.minusDays(1)).stream()
                .collect(Collectors.groupingBy(Task::getScheduledDate, Collectors.summingInt(Task::getEstimatedMinutes)));
        return planned.entrySet().stream()
                .filter(e -> e.getValue() > goal.getMinutesPerDay())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** Over the lookback period before the re-plan. */
    private Double completionRate(Long goalId, LocalDate from) {
        return Completion.rate(tasks.findByGoalIdAndScheduledDateBetween(goalId,
                from.minusDays(COMPLETION_LOOKBACK_DAYS), from.minusDays(1)));
    }

    private void apply(Goal goal, RePlanRequest request, PlanChanges changes, RevisionSource source, String model) {
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
                    t.setSequence(c.sequence());
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
        revision.setAiModel(model);
        revision.setSummary(changes.coachMessage());
        revisions.save(revision);
    }

    private Goal findGoal(Long goalId) {
        return goals.findById(goalId).orElseThrow(() -> new NotFoundException("Goal not found"));
    }

    private static TaskView view(Task t) {
        return new TaskView(t.getId(), t.getTitle(), t.getScheduledDate(), t.getEstimatedMinutes(),
                t.getMilestone() == null ? null : t.getMilestone().getId(), t.getStatus(), t.getSequence());
    }
}
