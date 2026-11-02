package com.aicoach.task;

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

    @GetMapping("/goals/{goalId}/tasks")
    public List<TaskResponse> forDay(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return taskService.forDay(user.id(), goalId, date);
    }

    @PatchMapping("/tasks/{id}")
    public TaskResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                               @Valid @RequestBody UpdateTaskRequest req) {
        return taskService.update(user.id(), id, req);
    }
}
