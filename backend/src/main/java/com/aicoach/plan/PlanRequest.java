package com.aicoach.plan;

import com.aicoach.goal.ExperienceLevel;

import java.time.LocalDate;

/**
 * Everything a generator needs to plan a goal.
 *
 * @param today         first day tasks may be scheduled (user's timezone)
 * @param taskWindowEnd last day tasks may be scheduled; milestones may run to the deadline
 */
public record PlanRequest(String title, String description, ExperienceLevel experienceLevel,
                          int minutesPerDay, LocalDate deadline, LocalDate today, LocalDate taskWindowEnd) {
}
