package com.aicoach.replan;

import com.aicoach.goal.ExperienceLevel;
import com.aicoach.replan.PlanChanges.Action;
import com.aicoach.replan.PlanChanges.TaskChange;
import com.aicoach.replan.RePlanRequest.MilestoneView;
import com.aicoach.replan.RePlanRequest.TaskView;
import com.aicoach.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReplanValidatorTest {

    static final LocalDate FROM = LocalDate.of(2026, 11, 3);
    static final RePlanRequest REQ = new RePlanRequest(1L, "Learn Java", null, ExperienceLevel.BEGINNER, 60,
            FROM.plusDays(60), FROM, FROM.plusDays(13), 0.8, null,
            List.of(new TaskView(10L, "Generics", FROM, 40, 100L, TaskStatus.PENDING),
                    new TaskView(11L, "Streams", FROM.plusDays(1), 40, 100L, TaskStatus.PENDING)),
            List.of(), List.of(new MilestoneView(100L, "Basics", FROM.plusDays(14))));

    final ReplanValidator validator = new ReplanValidator();

    @Test
    void acceptsValidChanges() {
        PlanChanges changes = new PlanChanges("Moved generics to give you a lighter day.", List.of(
                new TaskChange(Action.MOVE, 10L, null, FROM.plusDays(2), null, null),
                new TaskChange(Action.RESIZE, 11L, null, null, 50, null),
                new TaskChange(Action.ADD, null, "Review", FROM, 30, 100L)));

        assertThat(validator.validate(REQ, changes)).isEmpty();
    }

    @Test
    void emptyChangeListIsValid() {
        assertThat(validator.validate(REQ, new PlanChanges("On track, no changes.", List.of()))).isEmpty();
    }

    @Test
    void rejectsUnknownTaskIdsAndMilestones() {
        PlanChanges changes = new PlanChanges("x", List.of(
                new TaskChange(Action.REMOVE, 999L, null, null, null, null),
                new TaskChange(Action.ADD, null, "New", FROM, 20, 555L)));

        List<String> errors = validator.validate(REQ, changes);

        assertThat(errors).anyMatch(e -> e.contains("task id 999"));
        assertThat(errors).anyMatch(e -> e.contains("milestone id 555"));
    }

    @Test
    void rejectsDayOverLimitAfterChanges() {
        PlanChanges changes = new PlanChanges("x", List.of(
                new TaskChange(Action.MOVE, 11L, null, FROM, null, null)));

        assertThat(validator.validate(REQ, changes)).anyMatch(e -> e.contains("80 minutes") && e.contains(FROM.toString()));
    }

    @Test
    void removingFreesTimeForAdds() {
        PlanChanges changes = new PlanChanges("Swapped generics for a review session.", List.of(
                new TaskChange(Action.REMOVE, 10L, null, null, null, null),
                new TaskChange(Action.ADD, null, "Review", FROM, 60, null)));

        assertThat(validator.validate(REQ, changes)).isEmpty();
    }

    @Test
    void rejectsDatesOutsideWindowAndBadMinutes() {
        PlanChanges changes = new PlanChanges("x", List.of(
                new TaskChange(Action.MOVE, 10L, null, FROM.minusDays(1), null, null),
                new TaskChange(Action.ADD, null, "Far", FROM.plusDays(30), 20, null),
                new TaskChange(Action.RESIZE, 11L, null, null, 500, null)));

        assertThat(validator.validate(REQ, changes))
                .filteredOn(e -> e.contains("must be")).hasSize(3);
    }

    @Test
    void rejectsMissingCoachMessageOrAction() {
        List<String> errors = validator.validate(REQ, new PlanChanges(" ",
                List.of(new TaskChange(null, 10L, null, null, null, null))));

        assertThat(errors).contains("coachMessage is empty").anyMatch(e -> e.contains("no action"));
        assertThat(validator.validate(REQ, null)).isNotEmpty();
    }
}
