package com.aicoach.checkin;

import com.aicoach.plan.RevisionSource;
import com.aicoach.task.TaskDtos.TaskResponse;
import com.aicoach.task.TaskStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CheckInDtos {

    private CheckInDtos() {
    }

    public record CheckInRequest(
            @NotNull LocalDate date,
            @NotNull @Size(max = 50) List<@Valid @NotNull TaskResult> tasks,
            @Size(max = 2000) String note) {
    }

    public record TaskResult(@NotNull Long taskId, @NotNull TaskStatus status) {
    }

    public record CheckInResponse(Long id, LocalDate date, String note, String coachMessage,
                                  RevisionSource replanSource, List<TaskResponse> upcomingTasks) {
    }

    public record CheckInSummary(Long id, LocalDate date, String note, String coachMessage, Instant updatedAt) {
        static CheckInSummary from(CheckIn c) {
            return new CheckInSummary(c.getId(), c.getCheckInDate(), c.getNote(), c.getCoachMessage(), c.getUpdatedAt());
        }
    }

    public record NextMilestone(Long id, String title, LocalDate targetDate) {
    }

    /**
     * @param completionRate (done + half of partial minutes) / reported minutes so far, null before any report
     * @param daysBehind     extra days of work needed to reach the next milestone on time, 0 if on track
     */
    public record ProgressResponse(Long goalId, int totalTasks, int doneTasks, int partialTasks, int skippedTasks,
                                   int percentComplete, Double completionRate, NextMilestone nextMilestone,
                                   int daysBehind, boolean onTrack) {
    }
}
