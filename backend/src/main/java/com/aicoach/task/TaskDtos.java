package com.aicoach.task;

import com.aicoach.common.MaybeNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class TaskDtos {

    private TaskDtos() {
    }

    /** All fields optional: only non-null values are applied. */
    public record UpdateTaskRequest(
            @Size(min = 1, max = 200) String title,
            LocalDate scheduledDate,
            @Min(1) @Max(600) Integer estimatedMinutes,
            TaskStatus status) {
    }

    public record TaskResponse(Long id, Long goalId, @MaybeNull Long milestoneId, String title,
                               LocalDate scheduledDate, int estimatedMinutes, TaskStatus status, int sequence) {
        public static TaskResponse from(Task t) {
            return new TaskResponse(t.getId(), t.getGoal().getId(),
                    t.getMilestone() == null ? null : t.getMilestone().getId(),
                    t.getTitle(), t.getScheduledDate(), t.getEstimatedMinutes(), t.getStatus(), t.getSequence());
        }
    }
}
