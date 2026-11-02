package com.aicoach.checkin;

import com.aicoach.checkin.CheckInDtos.NextMilestone;
import com.aicoach.checkin.CheckInDtos.ProgressResponse;
import com.aicoach.goal.Goal;
import com.aicoach.goal.GoalService;
import com.aicoach.goal.Milestone;
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

        double reported = 0;
        double achieved = 0;
        for (Task t : all) {
            if (t.getStatus() == TaskStatus.PENDING) continue;
            reported += t.getEstimatedMinutes();
            if (t.getStatus() == TaskStatus.DONE) achieved += t.getEstimatedMinutes();
            if (t.getStatus() == TaskStatus.PARTIAL) achieved += t.getEstimatedMinutes() / 2.0;
        }
        Double completionRate = reported == 0 ? null : achieved / reported;

        Milestone next = goal.getMilestones().stream()
                .filter(m -> !m.getTargetDate().isBefore(today))
                .findFirst().orElse(null);
        int daysBehind = next == null ? 0 : daysBehind(goal, all, next, today);

        return new ProgressResponse(goal.getId(), all.size(), done, partial, skipped, percent, completionRate,
                next == null ? null : new NextMilestone(next.getId(), next.getTitle(), next.getTargetDate()),
                daysBehind, daysBehind == 0);
    }

    /** Pending work for milestones up to {@code next}, compared with the time left until its target date. */
    private static int daysBehind(Goal goal, List<Task> all, Milestone next, LocalDate today) {
        long remaining = all.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING && !t.getScheduledDate().isBefore(today))
                .filter(t -> t.getMilestone() != null && t.getMilestone().getPosition() <= next.getPosition())
                .mapToLong(Task::getEstimatedMinutes)
                .sum();
        long capacity = (ChronoUnit.DAYS.between(today, next.getTargetDate()) + 1) * goal.getMinutesPerDay();
        long excess = remaining - capacity;
        return excess <= 0 ? 0 : (int) Math.ceil((double) excess / goal.getMinutesPerDay());
    }

    private static int count(List<Task> all, TaskStatus status) {
        return (int) all.stream().filter(t -> t.getStatus() == status).count();
    }
}
