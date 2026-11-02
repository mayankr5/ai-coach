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

            for (PlannedTask t : m.tasks() == null ? List.<PlannedTask>of() : m.tasks()) {
                taskCount++;
                String tLabel = label + " task '" + t.title() + "'";
                checkTitle(t.title(), tLabel, errors);
                if (t.minutes() < 5 || t.minutes() > req.minutesPerDay()) {
                    errors.add(tLabel + " is " + t.minutes() + " minutes; must be 5-" + req.minutesPerDay());
                }
                if (t.date() == null) {
                    errors.add(tLabel + " has no date");
                } else if (t.date().isBefore(req.today()) || t.date().isAfter(req.taskWindowEnd())) {
                    errors.add(tLabel + " date " + t.date() + " must be between "
                            + req.today() + " and " + req.taskWindowEnd());
                } else {
                    minutesByDay.merge(t.date(), t.minutes(), Integer::sum);
                }
            }
        }

        if (taskCount == 0) {
            errors.add("Plan must schedule at least one task between " + req.today() + " and " + req.taskWindowEnd());
        }
        minutesByDay.forEach((day, total) -> {
            if (total > req.minutesPerDay()) {
                errors.add(day + " has " + total + " minutes of tasks; the limit is " + req.minutesPerDay());
            }
        });
        return errors;
    }

    private static void checkTitle(String title, String label, List<String> errors) {
        if (title == null || title.isBlank()) {
            errors.add(label + " has an empty title");
        } else if (title.length() > MAX_TITLE) {
            errors.add(label + " title is longer than " + MAX_TITLE + " characters");
        }
    }
}
