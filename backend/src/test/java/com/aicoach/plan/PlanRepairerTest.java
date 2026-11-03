package com.aicoach.plan;

import com.aicoach.goal.ExperienceLevel;
import com.aicoach.plan.GeneratedPlan.PlannedMilestone;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanRepairerTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    static final PlanRequest REQ = new PlanRequest("Learn Java", null, ExperienceLevel.BEGINNER,
            60, TODAY.plusDays(60), TODAY, TODAY.plusDays(13));

    final PlanRepairer repairer = new PlanRepairer();

    @Test
    void dropsTasksOutsideWindowAndKeepsTheRest() {
        // Like llama3's second attempt: tasks on 22 Nov, 29 Nov and the deadline
        GeneratedPlan plan = new GeneratedPlan(List.of(
                new PlannedMilestone("Basics", TODAY.plusDays(13), List.of(
                        new PlannedTask("Today", TODAY, 30),
                        new PlannedTask("Yesterday", TODAY.minusDays(1), 30))),
                new PlannedMilestone("Testing", TODAY.plusDays(40), List.of(
                        new PlannedTask("Later", TODAY.plusDays(20), 30),
                        new PlannedTask("Deadline", TODAY.plusDays(60), 30)))));

        PlanRepairer.Repaired result = repairer.repair(plan, REQ);

        assertThat(result.droppedTasks()).isEqualTo(3);
        assertThat(result.plan().milestones()).hasSize(2);
        assertThat(result.plan().milestones().get(0).tasks()).extracting(PlannedTask::title).containsExactly("Today");
        assertThat(result.plan().milestones().get(1).tasks()).isEmpty();
    }

    @Test
    void leavesRealProblemsForTheValidator() {
        // Over-long tasks and missing dates are not "harmless", so they stay and get rejected
        GeneratedPlan plan = new GeneratedPlan(List.of(new PlannedMilestone("M", TODAY.plusDays(13), List.of(
                new PlannedTask("Too long", TODAY, 500), new PlannedTask("No date", null, 30)))));

        PlanRepairer.Repaired result = repairer.repair(plan, REQ);

        assertThat(result.droppedTasks()).isZero();
        assertThat(result.plan().milestones().getFirst().tasks()).hasSize(2);
    }

    @Test
    void handlesNullPlan() {
        assertThat(repairer.repair(null, REQ).plan()).isNull();
        assertThat(repairer.repair(new GeneratedPlan(null), REQ).droppedTasks()).isZero();
    }
}
