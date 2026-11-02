package com.aicoach.plan;

import com.aicoach.goal.ExperienceLevel;
import com.aicoach.plan.GeneratedPlan.PlannedMilestone;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanValidatorTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    static final LocalDate DEADLINE = TODAY.plusDays(60);
    static final PlanRequest REQ = new PlanRequest("Learn Java", null, ExperienceLevel.BEGINNER,
            60, DEADLINE, TODAY, TODAY.plusDays(13));

    final PlanValidator validator = new PlanValidator();

    @Test
    void validPlanHasNoErrors() {
        assertThat(validator.validate(plan(dailyTasks(40)), REQ)).isEmpty();
    }

    @Test
    void rejectsDayOverDailyLimit() {
        List<PlannedTask> tasks = dailyTasks(40);
        tasks.add(new PlannedTask("Extra", TODAY, 40));

        assertThat(validator.validate(plan(tasks), REQ))
                .anyMatch(e -> e.contains("80 minutes") && e.contains("limit is 60"));
    }

    @Test
    void rejectsTasksOutsideWindow() {
        List<PlannedTask> tasks = dailyTasks(40);
        tasks.add(new PlannedTask("Past", TODAY.minusDays(1), 30));
        tasks.add(new PlannedTask("Too far", TODAY.plusDays(20), 30));

        assertThat(validator.validate(plan(tasks), REQ))
                .filteredOn(e -> e.contains("must be between")).hasSize(2);
    }

    @Test
    void rejectsMilestonesOutOfOrderOrAfterDeadline() {
        GeneratedPlan plan = new GeneratedPlan(List.of(
                new PlannedMilestone("Second", TODAY.plusDays(30), dailyTasks(40)),
                new PlannedMilestone("First", TODAY.plusDays(10), List.of()),
                new PlannedMilestone("Late", DEADLINE.plusDays(30), List.of())));

        List<String> errors = validator.validate(plan, REQ);

        assertThat(errors).anyMatch(e -> e.contains("before the previous milestone"));
        assertThat(errors).anyMatch(e -> e.contains("Milestone 3") && e.contains("deadline"));
    }

    @Test
    void rejectsMilestonesThatStopWellBeforeDeadline() {
        GeneratedPlan plan = new GeneratedPlan(List.of(
                new PlannedMilestone("Only", TODAY.plusDays(13), dailyTasks(40))));

        assertThat(validator.validate(plan, REQ))
                .anyMatch(e -> e.contains("last milestone ends") && e.contains(DEADLINE.toString()));
    }

    @Test
    void acceptsLastMilestoneWithinAWeekOfDeadline() {
        GeneratedPlan plan = new GeneratedPlan(List.of(
                new PlannedMilestone("Basics", TODAY.plusDays(13), dailyTasks(40)),
                new PlannedMilestone("Finish", DEADLINE.minusDays(7), List.of())));

        assertThat(validator.validate(plan, REQ)).isEmpty();
    }

    @Test
    void rejectsUnderUsedWindow() {
        // Like the weak llama3 plan: 5 x 30 min over a 14-day, 60 min/day window (840 available)
        List<PlannedTask> sparse = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sparse.add(new PlannedTask("Task " + i, TODAY.plusDays(i * 2), 30));
        }

        assertThat(validator.validate(plan(sparse), REQ))
                .anyMatch(e -> e.contains("total 150 minutes") && e.contains("at least 420"));
    }

    @Test
    void utilizationUsesShorterWindowWhenDeadlineIsClose() {
        LocalDate deadline = TODAY.plusDays(2);
        PlanRequest shortReq = new PlanRequest("Quick", null, ExperienceLevel.BEGINNER, 60, deadline, TODAY, deadline);
        GeneratedPlan plan = new GeneratedPlan(List.of(new PlannedMilestone("Done", deadline, List.of(
                new PlannedTask("A", TODAY, 30), new PlannedTask("B", TODAY.plusDays(1), 30),
                new PlannedTask("C", TODAY.plusDays(2), 30)))));

        // 90 of 180 available minutes = exactly 50%
        assertThat(validator.validate(plan, shortReq)).isEmpty();
    }

    @Test
    void rejectsEmptyPlanAndBlankTitles() {
        assertThat(validator.validate(new GeneratedPlan(List.of()), REQ)).containsExactly("Plan must contain at least one milestone");
        assertThat(validator.validate(null, REQ)).isNotEmpty();

        List<PlannedTask> tasks = dailyTasks(40);
        tasks.add(new PlannedTask(" ", TODAY.plusDays(1), 5));
        assertThat(validator.validate(plan(tasks), REQ)).anyMatch(e -> e.contains("empty title"));
    }

    @Test
    void rejectsPlanWithNoTasks() {
        GeneratedPlan plan = new GeneratedPlan(List.of(new PlannedMilestone("M", DEADLINE, List.of())));

        assertThat(validator.validate(plan, REQ)).anyMatch(e -> e.contains("at least one task"));
    }

    /** One task per day across the whole 14-day window. */
    private static List<PlannedTask> dailyTasks(int minutes) {
        List<PlannedTask> tasks = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            tasks.add(new PlannedTask("Day " + (i + 1), TODAY.plusDays(i), minutes));
        }
        return tasks;
    }

    /** Two milestones: the tasks in the first, the second ending on the deadline. */
    private static GeneratedPlan plan(List<PlannedTask> tasks) {
        return new GeneratedPlan(List.of(
                new PlannedMilestone("Basics", TODAY.plusDays(14), tasks),
                new PlannedMilestone("Projects", DEADLINE, List.of())));
    }
}
