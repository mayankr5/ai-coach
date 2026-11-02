package com.aicoach.plan;

import java.time.LocalDate;
import java.util.List;

/** The plan as the AI returns it, before validation. Field names double as the JSON schema. */
public record GeneratedPlan(List<PlannedMilestone> milestones) {

    public record PlannedMilestone(String title, LocalDate targetDate, List<PlannedTask> tasks) {
    }

    public record PlannedTask(String title, LocalDate date, int minutes) {
    }
}
