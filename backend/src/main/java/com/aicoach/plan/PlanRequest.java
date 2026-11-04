package com.aicoach.plan;

import com.aicoach.goal.ExperienceLevel;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Everything a generator needs to plan a goal.
 *
 * @param today         first day tasks may be scheduled (user's timezone)
 * @param taskWindowEnd last day tasks may be scheduled; milestones may run to the deadline
 * @param bufferDay     weekday that gets no tasks, kept free for catching up; null for none
 */
public record PlanRequest(String title, String description, ExperienceLevel experienceLevel,
                          int minutesPerDay, LocalDate deadline, LocalDate today, LocalDate taskWindowEnd,
                          DayOfWeek bufferDay) {

    public boolean isBufferDay(LocalDate date) {
        return bufferDay != null && date.getDayOfWeek() == bufferDay;
    }

    /** Buffer days inside the task window. */
    public List<LocalDate> bufferDates() {
        return today.datesUntil(taskWindowEnd.plusDays(1)).filter(this::isBufferDay).toList();
    }

    /** Days in the task window that may hold tasks. */
    public long workingDays() {
        return ChronoUnit.DAYS.between(today, taskWindowEnd) + 1 - bufferDates().size();
    }
}
