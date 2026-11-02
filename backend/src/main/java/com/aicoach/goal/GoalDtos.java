package com.aicoach.goal;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class GoalDtos {

    private GoalDtos() {
    }

    public record CreateGoalRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull @Future LocalDate deadline,
            @NotNull @Min(10) @Max(600) Integer minutesPerDay,
            @NotNull ExperienceLevel experienceLevel) {
    }

    /** All fields optional: only non-null values are applied. */
    public record UpdateGoalRequest(
            @Size(min = 1, max = 200) String title,
            @Size(max = 2000) String description,
            @Future LocalDate deadline,
            @Min(10) @Max(600) Integer minutesPerDay,
            ExperienceLevel experienceLevel,
            GoalStatus status) {
    }

    public record MilestoneResponse(Long id, String title, LocalDate targetDate, int position) {
        static MilestoneResponse from(Milestone m) {
            return new MilestoneResponse(m.getId(), m.getTitle(), m.getTargetDate(), m.getPosition());
        }
    }

    public record GoalSummary(Long id, String title, LocalDate deadline, GoalStatus status, Instant createdAt) {
        static GoalSummary from(Goal g) {
            return new GoalSummary(g.getId(), g.getTitle(), g.getDeadline(), g.getStatus(), g.getCreatedAt());
        }
    }

    public record GoalResponse(
            Long id, String title, String description, LocalDate deadline, int minutesPerDay,
            ExperienceLevel experienceLevel, GoalStatus status, Instant createdAt,
            List<MilestoneResponse> milestones) {
        static GoalResponse from(Goal g) {
            return new GoalResponse(g.getId(), g.getTitle(), g.getDescription(), g.getDeadline(),
                    g.getMinutesPerDay(), g.getExperienceLevel(), g.getStatus(), g.getCreatedAt(),
                    g.getMilestones().stream().map(MilestoneResponse::from).toList());
        }
    }
}
