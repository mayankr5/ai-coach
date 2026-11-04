package com.aicoach.plan;

import com.aicoach.plan.GeneratedPlan.PlannedTask;

import java.util.List;

/** One more week of tasks as the AI returns it, before validation. Field names double as the JSON schema. */
public record PlannedWeek(List<PlannedTask> tasks) {
}
