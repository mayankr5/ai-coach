package com.aicoach.task;

import com.aicoach.common.NotFoundException;
import com.aicoach.goal.Goal;
import com.aicoach.goal.GoalService;
import com.aicoach.task.TaskDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class TaskService {

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
        return tasks.findByGoalIdAndScheduledDateOrderByIdAsc(goal.getId(), day).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, UpdateTaskRequest req) {
        Task task = tasks.findOwned(taskId, userId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (req.title() != null) task.setTitle(req.title());
        if (req.scheduledDate() != null) task.setScheduledDate(req.scheduledDate());
        if (req.estimatedMinutes() != null) task.setEstimatedMinutes(req.estimatedMinutes());
        if (req.status() != null) task.setStatus(req.status());
        return TaskResponse.from(task);
    }
}
