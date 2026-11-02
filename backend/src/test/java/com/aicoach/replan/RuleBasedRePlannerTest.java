package com.aicoach.replan;

import com.aicoach.goal.ExperienceLevel;
import com.aicoach.replan.PlanChanges.Action;
import com.aicoach.replan.PlanChanges.TaskChange;
import com.aicoach.replan.RePlanRequest.TaskView;
import com.aicoach.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedRePlannerTest {

    static final LocalDate FROM = LocalDate.of(2026, 11, 3);

    final RuleBasedRePlanner planner = new RuleBasedRePlanner();

    @Test
    void placesSkippedTaskOnFirstDayWithRoom() {
        // FROM has 40 of 60 minutes used, so a 30-minute task goes to the next day
        RePlanRequest req = request(FROM.plusDays(30),
                List.of(new TaskView(1L, "Upcoming", FROM, 40, null, TaskStatus.PENDING)),
                List.of(new TaskView(2L, "Generics", FROM.minusDays(1), 30, 7L, TaskStatus.SKIPPED)));

        PlanChanges changes = planner.replan(req);

        assertThat(changes.changes()).containsExactly(
                new TaskChange(Action.ADD, null, "Generics", FROM.plusDays(1), 30, 7L));
        assertThat(changes.coachMessage()).contains("Moved 1 unfinished task");
    }

    @Test
    void partialTaskGetsHalfLengthFollowUp() {
        RePlanRequest req = request(FROM.plusDays(30), List.of(),
                List.of(new TaskView(2L, "Streams", FROM.minusDays(1), 40, null, TaskStatus.PARTIAL)));

        assertThat(planner.replan(req).changes()).containsExactly(
                new TaskChange(Action.ADD, null, "Finish: Streams", FROM, 20, null));
    }

    @Test
    void reportsWorkThatDoesNotFitBeforeDeadline() {
        RePlanRequest req = request(FROM,
                List.of(new TaskView(1L, "Full day", FROM, 60, null, TaskStatus.PENDING)),
                List.of(new TaskView(2L, "Generics", FROM.minusDays(1), 30, null, TaskStatus.SKIPPED)));

        PlanChanges changes = planner.replan(req);

        assertThat(changes.changes()).isEmpty();
        assertThat(changes.coachMessage()).contains("1 task doesn't fit before the deadline");
    }

    @Test
    void nothingMissedMeansNoChanges() {
        PlanChanges changes = planner.replan(request(FROM.plusDays(30), List.of(), List.of()));

        assertThat(changes.changes()).isEmpty();
        assertThat(changes.coachMessage()).contains("Nothing to catch up on");
    }

    private static RePlanRequest request(LocalDate deadline, List<TaskView> upcoming, List<TaskView> missed) {
        return new RePlanRequest(1L, "Learn Java", null, ExperienceLevel.BEGINNER, 60, deadline, FROM,
                FROM.plusDays(13), null, null, upcoming, missed, List.of());
    }
}
