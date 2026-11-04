package com.aicoach.adjust;

import com.aicoach.adjust.AdjustmentDtos.AdjustmentOptions;
import com.aicoach.adjust.AdjustmentDtos.ExtendOption;
import com.aicoach.adjust.AdjustmentDtos.MoreTimeOption;
import com.aicoach.adjust.Repacker.Item;
import com.aicoach.checkin.CheckInDtos.NextMilestone;
import com.aicoach.checkin.ProgressService;
import com.aicoach.checkin.ProgressService.Health;
import com.aicoach.common.BadRequestException;
import com.aicoach.common.ConflictException;
import com.aicoach.goal.Goal;
import com.aicoach.goal.GoalService;
import com.aicoach.goal.Milestone;
import com.aicoach.plan.PlanRevision;
import com.aicoach.plan.PlanRevisionRepository;
import com.aicoach.plan.RevisionSource;
import com.aicoach.task.Task;
import com.aicoach.task.TaskRepository;
import com.aicoach.task.TaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stage 3: when the next milestone no longer fits at the normal pace, offers ways back on track and applies the
 * one the user picks. Code works out every number; changes only ever touch tomorrow onwards.
 */
@Service
public class AdjustmentService {

    /** More daily time than this multiple of the current time is flagged as unlikely to last. */
    static final double REALISTIC_INCREASE = 1.5;
    static final int MAX_MINUTES_PER_DAY = 600;

    private final GoalService goalService;
    private final TaskRepository tasks;
    private final PlanRevisionRepository revisions;

    public AdjustmentService(GoalService goalService, TaskRepository tasks, PlanRevisionRepository revisions) {
        this.goalService = goalService;
        this.tasks = tasks;
        this.revisions = revisions;
    }

    @Transactional(readOnly = true)
    public AdjustmentOptions options(Long userId, Long goalId) {
        return options(goalService.findOwned(userId, goalId));
    }

    /** Moves the next milestone, every later one and the deadline; tasks stay where they are. */
    @Transactional
    public AdjustmentOptions extend(Long userId, Long goalId, int days) {
        Goal goal = goalService.findOwned(userId, goalId);
        Milestone next = nextMilestone(goal, today(goal));
        if (next == null) {
            throw new ConflictException("No upcoming milestone to move");
        }
        goal.getMilestones().stream().filter(m -> m.getPosition() >= next.getPosition())
                .forEach(m -> m.setTargetDate(m.getTargetDate().plusDays(days)));
        goal.setDeadline(goal.getDeadline().plusDays(days));
        record(goal, "Moved \"" + next.getTitle() + "\", later milestones and the deadline by " + days
                + (days == 1 ? " day" : " days"));
        return options(goal);
    }

    /** Raises the daily time and lays upcoming tasks out again, so the extra time is used from tomorrow. */
    @Transactional
    public AdjustmentOptions moreTime(Long userId, Long goalId, int minutesPerDay) {
        Goal goal = goalService.findOwned(userId, goalId);
        int current = goal.getMinutesPerDay();
        if (minutesPerDay <= current) {
            throw new BadRequestException("Choose more than the current " + current + " minutes a day");
        }
        goal.setMinutesPerDay(minutesPerDay);

        LocalDate tomorrow = today(goal).plusDays(1);
        List<Task> upcoming = tasks.findUpcomingPending(goal.getId(), tomorrow);
        Repacker.Result packed = Repacker.repack(items(upcoming), tomorrow, minutesPerDay, goal.getBufferDay(),
                goal.getDeadline());
        upcoming.forEach(t -> t.setScheduledDate(packed.dates().get(t.getId())));
        record(goal, "Daily time " + current + " → " + minutesPerDay + " minutes; " + upcoming.size()
                + " upcoming tasks laid out again");
        return options(goal);
    }

    private AdjustmentOptions options(Goal goal) {
        LocalDate today = today(goal);
        Milestone next = nextMilestone(goal, today);
        if (next == null) {
            return new AdjustmentOptions(0, 100, null, null, null);
        }
        List<Task> all = tasks.findByGoalId(goal.getId());
        Health health = ProgressService.health(goal, all, next, today);
        NextMilestone milestone = new NextMilestone(next.getId(), next.getTitle(), next.getTargetDate());
        if (health.minutesBehind() == 0) {
            return new AdjustmentOptions(0, health.percent(), milestone, null, null);
        }
        int days = extendDays(goal, all, next, health, today);
        return new AdjustmentOptions(health.minutesBehind(), health.percent(), milestone,
                new ExtendOption(days, next.getTargetDate().plusDays(days), goal.getDeadline().plusDays(days)),
                moreTimeOption(goal, next, today));
    }

    /** Enough days for the missing time, and for the milestone's last task to land on or before its new date. */
    private static int extendDays(Goal goal, List<Task> all, Milestone next, Health health, LocalDate today) {
        int forTime = (int) Math.ceil((double) health.minutesBehind() / goal.getMinutesPerDay());
        LocalDate lastTask = all.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING && !t.getScheduledDate().isBefore(today))
                .filter(t -> t.getMilestone() != null && t.getMilestone().getPosition() <= next.getPosition())
                .map(Task::getScheduledDate).max(LocalDate::compareTo).orElse(next.getTargetDate());
        int forLastTask = (int) Math.max(0, ChronoUnit.DAYS.between(next.getTargetDate(), lastTask));
        return Math.max(1, Math.max(forTime, forLastTask));
    }

    /**
     * Tries each daily time from the current one up and returns the first at which the re-pack puts every
     * task of the milestone (and earlier ones) on or before its date; null if none up to the maximum does.
     */
    private MoreTimeOption moreTimeOption(Goal goal, Milestone next, LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        if (next.getTargetDate().isBefore(tomorrow)) {
            return null;
        }
        List<Task> upcoming = tasks.findUpcomingPending(goal.getId(), tomorrow);
        Set<Long> due = upcoming.stream()
                .filter(t -> t.getMilestone() != null && t.getMilestone().getPosition() <= next.getPosition())
                .map(Task::getId).collect(Collectors.toSet());
        List<Item> items = items(upcoming);
        int current = goal.getMinutesPerDay();
        for (int minutes = current + 1; minutes <= MAX_MINUTES_PER_DAY; minutes++) {
            Map<Long, LocalDate> dates = Repacker.repack(items, tomorrow, minutes, goal.getBufferDay(),
                    goal.getDeadline()).dates();
            if (due.stream().allMatch(id -> !dates.get(id).isAfter(next.getTargetDate()))) {
                return new MoreTimeOption(minutes, current, minutes <= current * REALISTIC_INCREASE);
            }
        }
        return null;
    }

    private static List<Item> items(List<Task> tasksInSeriesOrder) {
        return tasksInSeriesOrder.stream().map(t -> new Item(t.getId(), t.getEstimatedMinutes())).toList();
    }

    private void record(Goal goal, String summary) {
        PlanRevision revision = new PlanRevision();
        revision.setGoal(goal);
        revision.setSource(RevisionSource.ADJUSTED);
        revision.setSummary(summary);
        revisions.save(revision);
    }

    private static Milestone nextMilestone(Goal goal, LocalDate today) {
        return goal.getMilestones().stream().filter(m -> !m.getTargetDate().isBefore(today)).findFirst().orElse(null);
    }

    private static LocalDate today(Goal goal) {
        return LocalDate.now(ZoneId.of(goal.getUser().getTimezone()));
    }
}
