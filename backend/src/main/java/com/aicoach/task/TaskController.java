package com.aicoach.task;

import com.aicoach.common.BadRequestException;
import com.aicoach.security.AuthUser;
import com.aicoach.task.TaskDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** One day ({@code date}, default today in the user's timezone) or a range ({@code from} and {@code to}). */
    @GetMapping("/goals/{goalId}/tasks")
    public List<TaskResponse> list(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                   LocalDate date,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                   LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                   LocalDate to) {
        if (from == null && to == null) {
            return taskService.forDay(user.id(), goalId, date);
        }
        if (date != null || from == null || to == null) {
            throw new BadRequestException("Use either date, or both from and to");
        }
        return taskService.between(user.id(), goalId, from, to);
    }

    @PatchMapping("/tasks/{id}")
    public TaskResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                               @Valid @RequestBody UpdateTaskRequest req) {
        return taskService.update(user.id(), id, req);
    }
}
