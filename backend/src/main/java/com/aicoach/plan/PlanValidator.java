package com.aicoach.plan;

import com.aicoach.plan.GeneratedPlan.PlannedMilestone;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Hard rules the AI can't override. Returns human-readable errors; empty means valid. */
@Component
public class PlanValidator {

    static final int MAX_TITLE = 200;
    static final int MAX_MILESTONES = 12;
    /** The final milestone may end at most this many days before the deadline. */
    static final int MAX_DAYS_SHORT_OF_DEADLINE = 7;
    /** Planned minutes in the task window must reach this share of the time the user has. */
    static final double MIN_WINDOW_UTILIZATION = 0.5;

    public List<String> validate(GeneratedPlan plan, PlanRequest req) {
        List<String> errors = new ArrayList<>();
        if (plan == null || plan.milestones() == null || plan.milestones().isEmpty()) {
            return List.of("Plan must contain at least one milestone");
        }
        if (plan.milestones().size() > MAX_MILESTONES) {
            errors.add("Plan has " + plan.milestones().size() + " milestones; maximum is " + MAX_MILESTONES);
        }

        Map<LocalDate, Integer> minutesByDay = new TreeMap<>();
        LocalDate previousTarget = null;
        int taskCount = 0;

        for (int i = 0; i < plan.milestones().size(); i++) {
            PlannedMilestone m = plan.milestones().get(i);
            String label = "Milestone " + (i + 1);
            checkTitle(m.title(), label, errors);

            if (m.targetDate() == null) {
                errors.add(label + " has no targetDate");
            } else {
                if (m.targetDate().isBefore(req.today()) || m.targetDate().isAfter(req.deadline())) {
                    errors.add(label + " targetDate " + m.targetDate() + " must be between "
                            + req.today() + " and the deadline " + req.deadline());
                }
                if (previousTarget != null && m.targetDate().isBefore(previousTarget)) {
                    errors.add(label + " targetDate " + m.targetDate() + " is before the previous milestone's");
                }
                previousTarget = m.targetDate();
            }

            List<PlannedTask> tasks = m.tasks() == null ? List.of() : m.tasks();
            taskCount += tasks.size();
            checkTasks(tasks, label + " task ", req, errors, minutesByDay);
        }

        if (previousTarget != null
                && previousTarget.isBefore(req.deadline().minusDays(MAX_DAYS_SHORT_OF_DEADLINE))) {
            errors.add("The last milestone ends on " + previousTarget + "; milestones must cover the goal up to "
                    + "the deadline " + req.deadline() + " (within " + MAX_DAYS_SHORT_OF_DEADLINE + " days)");
        }
        checkWindow(taskCount, minutesByDay, req, errors);
        return errors;
    }

    /**
     * A week of tasks added to an existing plan: the same task rules as a full plan, with {@code window}'s
     * today and taskWindowEnd as the week's first and last day.
     */
    public List<String> validateWeek(PlannedWeek week, PlanRequest window) {
        List<PlannedTask> tasks = week == null || week.tasks() == null ? List.of() : week.tasks();
        List<String> errors = new ArrayList<>();
        Map<LocalDate, Integer> minutesByDay = new TreeMap<>();
        checkTasks(tasks, "Task ", window, errors, minutesByDay);
        checkWindow(tasks.size(), minutesByDay, window, errors);
        return errors;
    }

    private static void checkTasks(List<PlannedTask> tasks, String labelPrefix, PlanRequest req, List<String> errors,
                                   Map<LocalDate, Integer> minutesByDay) {
        for (PlannedTask t : tasks) {
            if (t == null) {
                errors.add(labelPrefix.strip() + " is empty");
                continue;
            }
            String tLabel = labelPrefix + "'" + t.title() + "'";
            checkTitle(t.title(), tLabel, errors);
            if (t.minutes() < 5 || t.minutes() > req.minutesPerDay()) {
                errors.add(tLabel + " is " + t.minutes() + " minutes; must be 5-" + req.minutesPerDay());
            }
            if (t.date() == null) {
                errors.add(tLabel + " has no date");
            } else if (t.date().isBefore(req.today()) || t.date().isAfter(req.taskWindowEnd())) {
                errors.add(tLabel + " date " + t.date() + " must be between "
                        + req.today() + " and " + req.taskWindowEnd());
            } else if (req.isBufferDay(t.date())) {
                errors.add(tLabel + " is on " + t.date() + ", a buffer day; buffer days must have no tasks");
            } else {
                minutesByDay.merge(t.date(), t.minutes(), Integer::sum);
            }
        }
    }

    /** Enough work in the window, and no day over the limit. */
    private static void checkWindow(int taskCount, Map<LocalDate, Integer> minutesByDay, PlanRequest req,
                                    List<String> errors) {
        long available = req.workingDays() * req.minutesPerDay();
        long minimum = Math.round(available * MIN_WINDOW_UTILIZATION);
        int planned = minutesByDay.values().stream().mapToInt(Integer::intValue).sum();
        if (taskCount > 0 && planned < minimum) {
            errors.add("Tasks from " + req.today() + " to " + req.taskWindowEnd() + " total " + planned
                    + " minutes; plan at least " + minimum + " of the " + available + " available minutes");
        }

        if (taskCount == 0) {
            errors.add("Plan must schedule at least one task between " + req.today() + " and " + req.taskWindowEnd());
        }
        minutesByDay.forEach((day, total) -> {
            if (total > req.minutesPerDay()) {
                errors.add(day + " has " + total + " minutes of tasks; the limit is " + req.minutesPerDay());
            }
        });
    }

    private static void checkTitle(String title, String label, List<String> errors) {
        if (title == null || title.isBlank()) {
            errors.add(label + " has an empty title");
        } else if (title.length() > MAX_TITLE) {
            errors.add(label + " title is longer than " + MAX_TITLE + " characters");
        }
    }
}
