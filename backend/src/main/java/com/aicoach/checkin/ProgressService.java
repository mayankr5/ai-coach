package com.aicoach.checkin;

import com.aicoach.checkin.CheckInDtos.NextMilestone;
import com.aicoach.checkin.CheckInDtos.ProgressResponse;
import com.aicoach.goal.Goal;
import com.aicoach.goal.GoalService;
import com.aicoach.goal.Milestone;
import com.aicoach.task.Completion;
import com.aicoach.task.Task;
import com.aicoach.task.TaskRepository;
import com.aicoach.task.TaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Progress numbers are computed with plain arithmetic, never by the AI, so they are always consistent. */
@Service
public class ProgressService {

    private final GoalService goalService;
    private final TaskRepository tasks;

    public ProgressService(GoalService goalService, TaskRepository tasks) {
        this.goalService = goalService;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public ProgressResponse progress(Long userId, Long goalId) {
        Goal goal = goalService.findOwned(userId, goalId);
        LocalDate today = LocalDate.now(ZoneId.of(goal.getUser().getTimezone()));
        List<Task> all = tasks.findByGoalId(goal.getId());

        int done = count(all, TaskStatus.DONE);
        int partial = count(all, TaskStatus.PARTIAL);
        int skipped = count(all, TaskStatus.SKIPPED);
        int percent = all.isEmpty() ? 0 : (int) Math.round(100.0 * done / all.size());

        Double completionRate = Completion.rate(all);

        Milestone next = goal.getMilestones().stream()
                .filter(m -> !m.getTargetDate().isBefore(today))
                .findFirst().orElse(null);
        Health health = next == null ? Health.ON_TRACK : health(goal, all, next, today);
        int daysBehind = (int) Math.ceil((double) health.minutesBehind() / goal.getMinutesPerDay());

        return new ProgressResponse(goal.getId(), all.size(), done, partial, skipped, percent, completionRate,
                next == null ? null : new NextMilestone(next.getId(), next.getTitle(), next.getTargetDate()),
                health.percent(), health.minutesBehind(), daysBehind, health.minutesBehind() == 0);
    }

    public record Health(int percent, int minutesBehind) {
        static final Health ON_TRACK = new Health(100, 0);
    }

    /**
     * Pending work for milestones up to {@code next}, compared with the time left until its target date.
     * Every day counts at the normal daily time, buffer days included (that is what they are for); extra
     * minutes don't, as they are meant for short bursts, not every day.
     */
    public static Health health(Goal goal, List<Task> all, Milestone next, LocalDate today) {
        long work = all.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING && !t.getScheduledDate().isBefore(today))
                .filter(t -> t.getMilestone() != null && t.getMilestone().getPosition() <= next.getPosition())
                .mapToLong(Task::getEstimatedMinutes)
                .sum();
        long time = (ChronoUnit.DAYS.between(today, next.getTargetDate()) + 1) * goal.getMinutesPerDay();
        if (work <= time) {
            return Health.ON_TRACK;
        }
        // Rounded down, so any shortfall shows as below 100%
        return new Health((int) (100 * time / work), (int) (work - time));
    }

    private static int count(List<Task> all, TaskStatus status) {
        return (int) all.stream().filter(t -> t.getStatus() == status).count();
    }
}
