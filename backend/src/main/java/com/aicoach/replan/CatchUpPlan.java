package com.aicoach.replan;

import com.aicoach.replan.PlanChanges.TaskChange;

import java.time.LocalDate;
import java.util.List;

/**
 * What the catch-up scheduler decided, with enough detail to explain it to the user.
 *
 * @param followUps tasks added for missed or partly done work, in order
 * @param shifted   number of existing tasks moved to a later date to keep the order
 * @param extraDays days that use the goal's extra minutes
 * @param overflow  number of tasks that don't fit before the deadline (placed on the deadline anyway)
 */
public record CatchUpPlan(List<TaskChange> changes, List<TaskChange> followUps, int shifted,
                          List<LocalDate> extraDays, int overflow) {

    public boolean isEmpty() {
        return changes.isEmpty() && overflow == 0;
    }
}
