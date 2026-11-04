package com.aicoach.adjust;

import com.aicoach.goal.ExperienceLevel;

import java.time.LocalDate;
import java.util.List;

/**
 * What the AI needs to suggest work to drop.
 *
 * @param minutesToFree how much work must go for the milestone to fit
 * @param candidates    upcoming tasks that count towards the milestone, in series order; numbered from 1
 * @param done          titles of recently done tasks, so the suggestion builds on what's learned
 */
public record CutRequest(String goalTitle, String goalDescription, ExperienceLevel experienceLevel,
                         String milestoneTitle, LocalDate milestoneDate, int minutesToFree,
                         List<Candidate> candidates, List<String> done) {

    public record Candidate(long taskId, String title, int minutes, LocalDate date, String milestoneTitle) {
    }
}
