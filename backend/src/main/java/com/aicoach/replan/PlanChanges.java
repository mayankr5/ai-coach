package com.aicoach.replan;

import java.time.LocalDate;
import java.util.List;

/**
 * A re-plan expressed as edits to upcoming tasks, not a whole new plan: smaller output for the
 * model, and every change can be explained to the user.
 */
public record PlanChanges(String coachMessage, List<TaskChange> changes) {

    public enum Action {
        /** Change an upcoming task's date. Needs taskId and date. */
        MOVE,
        /** Change an upcoming task's length. Needs taskId and minutes. */
        RESIZE,
        /** Drop an upcoming task. Needs taskId. */
        REMOVE,
        /** Schedule a new task. Needs title, date, minutes and sequence; milestoneId optional. */
        ADD
    }

    public record TaskChange(Action action, Long taskId, String title, LocalDate date, Integer minutes,
                             Long milestoneId, Integer sequence) {
    }
}
