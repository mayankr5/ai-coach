package com.aicoach.task;

import com.aicoach.common.BadRequestException;
import com.aicoach.common.NotFoundException;
import com.aicoach.goal.Goal;
import com.aicoach.goal.GoalService;
import com.aicoach.task.TaskDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class TaskService {

    static final int MAX_RANGE_DAYS = 62;

    private final TaskRepository tasks;
    private final GoalService goalService;

    public TaskService(TaskRepository tasks, GoalService goalService) {
        this.tasks = tasks;
        this.goalService = goalService;
    }

    /** Tasks for one day; with no date, "today" is computed in the goal owner's timezone. */
    @Transactional(readOnly = true)
    public List<TaskResponse> forDay(Long userId, Long goalId, LocalDate date) {
        Goal goal = goalService.findOwned(userId, goalId);
        LocalDate day = date != null ? date : LocalDate.now(ZoneId.of(goal.getUser().getTimezone()));
        return tasks.findByGoalIdAndScheduledDateOrderBySequenceAscIdAsc(goal.getId(), day).stream()
                .map(TaskResponse::from)
                .toList();
    }

    /** Tasks from {@code from} to {@code to} inclusive, by day and then by place in the series. */
    @Transactional(readOnly = true)
    public List<TaskResponse> between(Long userId, Long goalId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BadRequestException("to must not be before from");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BadRequestException("A range can cover at most " + MAX_RANGE_DAYS + " days");
        }
        Goal goal = goalService.findOwned(userId, goalId);
        return tasks.findByGoalIdAndScheduledDateBetween(goal.getId(), from, to).stream()
                .sorted(Task.DAY_ORDER)
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, UpdateTaskRequest req) {
        Task task = tasks.findOwned(taskId, userId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (req.title() != null) task.setTitle(req.title());
        if (req.scheduledDate() != null && !req.scheduledDate().equals(task.getScheduledDate())) {
            // Keeps the series order matching the dates: the moved task goes last on its new day
            task.setSequence(tasks.maxSequenceUntil(task.getGoal().getId(), req.scheduledDate()) + 1);
            task.setScheduledDate(req.scheduledDate());
        }
        if (req.estimatedMinutes() != null) task.setEstimatedMinutes(req.estimatedMinutes());
        if (req.status() != null) task.setStatus(req.status());
        return TaskResponse.from(task);
    }
}
