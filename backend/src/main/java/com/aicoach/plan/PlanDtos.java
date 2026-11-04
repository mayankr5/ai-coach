package com.aicoach.plan;

import com.aicoach.task.TaskDtos.TaskResponse;
import com.aicoach.task.TaskStatus;

import java.time.LocalDate;
import java.util.List;

public final class PlanDtos {

    private PlanDtos() {
    }

    /** One more week added to a plan by {@link PlanExtender}. */
    public record ExtensionResponse(LocalDate from, LocalDate to, List<TaskResponse> tasks) {
    }

    public record PlanResponse(Long goalId, List<MilestonePlan> milestones) {
    }

    public record MilestonePlan(Long id, String title, LocalDate targetDate, int position, List<PlanTask> tasks) {
    }

    public record PlanTask(Long id, String title, LocalDate scheduledDate, int estimatedMinutes, TaskStatus status) {
    }
}
