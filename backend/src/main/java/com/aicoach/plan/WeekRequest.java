package com.aicoach.plan;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything needed to plan one more week of an existing plan.
 *
 * @param window         the goal, with today and taskWindowEnd set to the week's first and last day
 * @param milestones     which milestone each part of the week belongs to, in date order
 * @param done           titles of the most recently done tasks, oldest first
 * @param pending        titles of tasks still planned before the week, in order
 * @param completionRate share of planned work done over the last two weeks, null if nothing was reported
 */
public record WeekRequest(PlanRequest window, List<MilestoneSpan> milestones, List<String> done,
                          List<String> pending, Double completionRate) {

    public record MilestoneSpan(String title, LocalDate targetDate, LocalDate from, LocalDate to) {
    }
}
