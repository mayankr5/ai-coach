package com.aicoach.plan;

import com.aicoach.ai.AiResult;
import com.aicoach.checkin.CheckIn;
import com.aicoach.checkin.CheckInRepository;
import com.aicoach.common.BadRequestException;
import com.aicoach.common.ConflictException;
import com.aicoach.goal.Goal;
import com.aicoach.goal.GoalRepository;
import com.aicoach.goal.GoalService;
import com.aicoach.goal.GoalStatus;
import com.aicoach.goal.Milestone;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
import com.aicoach.plan.PlanDtos.ExtensionResponse;
import com.aicoach.plan.WeekRequest.MilestoneSpan;
import com.aicoach.task.Completion;
import com.aicoach.task.Task;
import com.aicoach.task.TaskDtos.TaskResponse;
import com.aicoach.task.TaskRepository;
import com.aicoach.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps a plan from running out: the first plan only has tasks for the next two weeks, so one more week is
 * planned whenever fewer than {@value #MIN_DAYS_AHEAD} days are left. The AI writes the tasks; code picks the
 * dates, checks the answer, assigns milestones by date and continues the series. As with the first plan, the
 * AI call runs between two short transactions.
 */
@Service
public class PlanExtender {

    private static final Logger log = LoggerFactory.getLogger(PlanExtender.class);

    static final int WEEK_DAYS = 7;
    /** A top-up is due once fewer than this many days, today included, have tasks. */
    static final int MIN_DAYS_AHEAD = 7;
    /** Planning ahead on request stops here, so repeated calls can't run away. */
    static final int MAX_DAYS_AHEAD = 28;
    /** No automatic top-ups for a goal with no check-in (or creation) in this many days. */
    static final int INACTIVE_AFTER_DAYS = 14;
    static final int RECENT_DONE = 15;
    static final int MAX_PENDING = 20;
    static final int COMPLETION_LOOKBACK_DAYS = 14;

    private final GoalRepository goals;
    private final GoalService goalService;
    private final TaskRepository tasks;
    private final CheckInRepository checkIns;
    private final PlanRevisionRepository revisions;
    private final WeekPlanner planner;
    private final PlanValidator validator;
    private final TransactionTemplate tx;

    public PlanExtender(GoalRepository goals, GoalService goalService, TaskRepository tasks, CheckInRepository checkIns,
                        PlanRevisionRepository revisions, WeekPlanner planner, PlanValidator validator,
                        TransactionTemplate tx) {
        this.goals = goals;
        this.goalService = goalService;
        this.tasks = tasks;
        this.checkIns = checkIns;
        this.revisions = revisions;
        this.planner = planner;
        this.validator = validator;
        this.tx = tx;
    }

    /**
     * Nightly: plans the next week if the plan runs out soon and the goal is still in use.
     *
     * @return true if tasks were added
     * @throws AiUnavailableException if the week was due but no model produced a usable one
     */
    public boolean extendIfDue(Long goalId) {
        WeekRequest request = tx.execute(status -> {
            Goal goal = goals.findById(goalId).orElseThrow();
            LocalDate today = today(goal);
            LocalDate last = tasks.findLastScheduledDate(goalId).orElse(null);
            if (goal.getStatus() != GoalStatus.ACTIVE || last == null || !recentlyUsed(goal, today)
                    || !last.isBefore(today.plusDays(MIN_DAYS_AHEAD - 1))) {
                return null;
            }
            return buildRequest(goal, today, last);
        });
        return request != null && !add(goalId, request).isEmpty();
    }

    /** On request: plans the week after the last planned task, up to {@value #MAX_DAYS_AHEAD} days ahead. */
    public ExtensionResponse extendNow(Long userId, Long goalId) {
        WeekRequest request = tx.execute(status -> {
            Goal goal = goalService.findOwned(userId, goalId);
            if (goal.getStatus() != GoalStatus.ACTIVE) {
                throw new BadRequestException("Only active goals can be planned ahead");
            }
            LocalDate today = today(goal);
            LocalDate last = tasks.findLastScheduledDate(goalId)
                    .orElseThrow(() -> new ConflictException("Goal has no plan yet; generate one first"));
            if (!last.isBefore(today.plusDays(MAX_DAYS_AHEAD - 1))) {
                throw new ConflictException("Already planned up to " + last + "; that's far enough ahead");
            }
            WeekRequest req = buildRequest(goal, today, last);
            if (req == null) {
                throw new ConflictException("The plan already reaches the deadline " + goal.getDeadline());
            }
            return req;
        });
        List<TaskResponse> added = add(goalId, request);
        if (added.isEmpty()) {
            throw new ConflictException("The plan was extended at the same time; reload it");
        }
        return new ExtensionResponse(request.window().today(), request.window().taskWindowEnd(), added);
    }

    /** Asks for the week, then saves it unless another top-up got there first. */
    private List<TaskResponse> add(Long goalId, WeekRequest request) {
        AiResult<PlannedWeek> result = planner.plan(request);
        // Checked again whatever the planner promises: nothing invalid ever reaches the database
        List<String> errors = validator.validateWeek(result.value(), request.window());
        if (!errors.isEmpty()) {
            throw new AiUnavailableException("AI returned an invalid week: " + String.join("; ", errors));
        }
        List<TaskResponse> added = tx.execute(status -> save(goals.findById(goalId).orElseThrow(), request, result));
        if (!added.isEmpty()) {
            log.info("Planned {} to {} for goal {} with {}: {} tasks", request.window().today(),
                    request.window().taskWindowEnd(), goalId, result.model(), added.size());
        }
        return added;
    }

    /** @return null when the plan already reaches the deadline */
    private WeekRequest buildRequest(Goal goal, LocalDate today, LocalDate last) {
        // Never today: its list stays as the user saw it
        LocalDate from = last.isBefore(today) ? today.plusDays(1) : last.plusDays(1);
        if (from.isAfter(goal.getDeadline())) {
            return null;
        }
        LocalDate to = from.plusDays(WEEK_DAYS - 1);
        if (to.isAfter(goal.getDeadline())) {
            to = goal.getDeadline();
        }
        PlanRequest window = new PlanRequest(goal.getTitle(), goal.getDescription(), goal.getExperienceLevel(),
                goal.getMinutesPerDay(), goal.getDeadline(), from, to, goal.getBufferDay());
        if (window.workingDays() == 0) {
            return null;
        }

        List<Task> all = tasks.findByGoalId(goal.getId()).stream().sorted(Task.DAY_ORDER).toList();
        List<String> done = all.stream().filter(t -> t.getStatus() == TaskStatus.DONE).map(Task::getTitle).toList();
        List<String> pending = all.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING && !t.getScheduledDate().isBefore(today))
                .map(Task::getTitle).limit(MAX_PENDING).toList();
        Double completionRate = Completion.rate(all.stream()
                .filter(t -> !t.getScheduledDate().isBefore(today.minusDays(COMPLETION_LOOKBACK_DAYS))
                        && t.getScheduledDate().isBefore(today))
                .toList());
        return new WeekRequest(window, spans(goal, from, to),
                done.subList(Math.max(0, done.size() - RECENT_DONE), done.size()), pending, completionRate);
    }

    private List<TaskResponse> save(Goal goal, WeekRequest request, AiResult<PlannedWeek> result) {
        LocalDate from = request.window().today();
        LocalDate last = tasks.findLastScheduledDate(goal.getId()).orElse(null);
        if (last != null && !last.isBefore(from)) {
            return List.of();
        }
        int sequence = last == null ? 0 : tasks.maxSequenceUntil(goal.getId(), last);

        List<PlannedTask> planned = new ArrayList<>(result.value().tasks());
        // The series: by day, and within a day in the order the AI listed the tasks (the sort is stable)
        planned.sort(Comparator.comparing(PlannedTask::date));
        List<Task> added = new ArrayList<>();
        for (PlannedTask pt : planned) {
            Task task = new Task();
            task.setGoal(goal);
            task.setMilestone(milestoneOn(goal, pt.date()));
            task.setTitle(pt.title().strip());
            task.setScheduledDate(pt.date());
            task.setEstimatedMinutes(pt.minutes());
            sequence += Task.SEQUENCE_STEP;
            task.setSequence(sequence);
            added.add(task);
        }
        tasks.saveAll(added);

        PlanRevision revision = new PlanRevision();
        revision.setGoal(goal);
        revision.setSource(RevisionSource.EXTENDED);
        revision.setAiModel(result.model());
        revision.setSummary("Planned " + from + " to " + request.window().taskWindowEnd() + ": " + added.size() + " tasks");
        revisions.save(revision);
        return added.stream().map(TaskResponse::from).toList();
    }

    /** Consecutive days of the week grouped by the milestone they fall in. */
    private static List<MilestoneSpan> spans(Goal goal, LocalDate from, LocalDate to) {
        List<MilestoneSpan> spans = new ArrayList<>();
        Milestone current = null;
        LocalDate start = from;
        for (LocalDate d = from; !d.isAfter(to.plusDays(1)); d = d.plusDays(1)) {
            Milestone m = d.isAfter(to) ? null : milestoneOn(goal, d);
            if (current != null && m != current) {
                spans.add(new MilestoneSpan(current.getTitle(), current.getTargetDate(), start, d.minusDays(1)));
            }
            if (m != current) {
                current = m;
                start = d;
            }
        }
        return spans;
    }

    /** The first milestone not yet due on that date; the last one if all are. */
    private static Milestone milestoneOn(Goal goal, LocalDate date) {
        List<Milestone> milestones = goal.getMilestones();
        return milestones.stream().filter(m -> !m.getTargetDate().isBefore(date)).findFirst()
                .orElse(milestones.isEmpty() ? null : milestones.getLast());
    }

    /** Created or checked in within {@value #INACTIVE_AFTER_DAYS} days. */
    private boolean recentlyUsed(Goal goal, LocalDate today) {
        ZoneId zone = ZoneId.of(goal.getUser().getTimezone());
        LocalDate lastSeen = checkIns.findFirstByGoalIdOrderByCheckInDateDesc(goal.getId())
                .map(CheckIn::getCheckInDate)
                .filter(d -> d.isAfter(goal.getCreatedAt().atZone(zone).toLocalDate()))
                .orElse(goal.getCreatedAt().atZone(zone).toLocalDate());
        return !lastSeen.isBefore(today.minusDays(INACTIVE_AFTER_DAYS));
    }

    private static LocalDate today(Goal goal) {
        return LocalDate.now(ZoneId.of(goal.getUser().getTimezone()));
    }
}
