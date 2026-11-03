package com.aicoach.replan;

import com.aicoach.goal.ExperienceLevel;
import com.aicoach.task.TaskStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Snapshot of a goal for re-planning.
 *
 * @param extraMinutesPerDay allowed on top of minutesPerDay on catch-up days
 * @param from           first date that may be changed; earlier days are history
 * @param windowEnd      last date tasks may be scheduled on
 * @param completionRate share of planned minutes actually done over the last two weeks, null if no data
 * @param upcoming       PENDING tasks from {@code from} on; the only tasks a re-plan may edit
 * @param missed         skipped or partial tasks not yet carried forward
 */
public record RePlanRequest(Long goalId, String title, String description, ExperienceLevel experienceLevel,
                            int minutesPerDay, int extraMinutesPerDay, LocalDate deadline, LocalDate from, LocalDate windowEnd,
                            Double completionRate, String note, List<TaskView> upcoming, List<TaskView> missed,
                            List<MilestoneView> milestones) {

    public record TaskView(Long id, String title, LocalDate date, int minutes, Long milestoneId, TaskStatus status) {
    }

    public record MilestoneView(Long id, String title, LocalDate targetDate) {
    }
}
