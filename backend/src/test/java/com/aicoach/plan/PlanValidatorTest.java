package com.aicoach.plan;

import com.aicoach.goal.ExperienceLevel;
import com.aicoach.plan.GeneratedPlan.PlannedMilestone;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanValidatorTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    static final PlanRequest REQ = new PlanRequest("Learn Java", null, ExperienceLevel.BEGINNER,
            60, TODAY.plusDays(60), TODAY, TODAY.plusDays(13));

    final PlanValidator validator = new PlanValidator();

    @Test
    void validPlanHasNoErrors() {
        GeneratedPlan plan = new GeneratedPlan(List.of(
                new PlannedMilestone("Basics", TODAY.plusDays(14), List.of(
                        new PlannedTask("Install JDK", TODAY, 30),
                        new PlannedTask("Hello world", TODAY, 30))),
                new PlannedMilestone("OOP", TODAY.plusDays(40), List.of())));

        assertThat(validator.validate(plan, REQ)).isEmpty();
    }

    @Test
    void rejectsDayOverDailyLimit() {
        GeneratedPlan plan = single(new PlannedTask("A", TODAY, 40), new PlannedTask("B", TODAY, 40));

        assertThat(validator.validate(plan, REQ)).anyMatch(e -> e.contains("80 minutes") && e.contains("limit is 60"));
    }

    @Test
    void rejectsTasksOutsideWindow() {
        GeneratedPlan plan = single(new PlannedTask("Past", TODAY.minusDays(1), 30),
                new PlannedTask("Too far", TODAY.plusDays(20), 30));

        assertThat(validator.validate(plan, REQ)).hasSize(2).allMatch(e -> e.contains("must be between"));
    }

    @Test
    void rejectsMilestonesOutOfOrderOrAfterDeadline() {
        GeneratedPlan plan = new GeneratedPlan(List.of(
                new PlannedMilestone("Second", TODAY.plusDays(30), List.of(new PlannedTask("A", TODAY, 30))),
                new PlannedMilestone("First", TODAY.plusDays(10), List.of()),
                new PlannedMilestone("Late", TODAY.plusDays(90), List.of())));

        List<String> errors = validator.validate(plan, REQ);

        assertThat(errors).anyMatch(e -> e.contains("before the previous milestone"));
        assertThat(errors).anyMatch(e -> e.contains("Milestone 3") && e.contains("deadline"));
    }

    @Test
    void rejectsEmptyPlanAndBlankTitles() {
        assertThat(validator.validate(new GeneratedPlan(List.of()), REQ)).containsExactly("Plan must contain at least one milestone");
        assertThat(validator.validate(null, REQ)).isNotEmpty();
        assertThat(validator.validate(single(new PlannedTask(" ", TODAY, 30)), REQ)).anyMatch(e -> e.contains("empty title"));
    }

    @Test
    void rejectsPlanWithNoTasks() {
        GeneratedPlan plan = new GeneratedPlan(List.of(new PlannedMilestone("M", TODAY.plusDays(10), List.of())));

        assertThat(validator.validate(plan, REQ)).anyMatch(e -> e.contains("at least one task"));
    }

    private static GeneratedPlan single(PlannedTask... tasks) {
        return new GeneratedPlan(List.of(new PlannedMilestone("M", TODAY.plusDays(14), List.of(tasks))));
    }
}
