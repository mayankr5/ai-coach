package com.aicoach.replan;

import com.aicoach.replan.PlanChanges.TaskChange;
import com.aicoach.replan.RePlanRequest.MilestoneView;
import com.aicoach.replan.RePlanRequest.TaskView;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/** Checks AI-proposed changes by applying them to a copy of the upcoming tasks. Empty result means valid. */
@Component
public class ReplanValidator {

    static final int MAX_CHANGES = 60;
    static final int MAX_TITLE = 200;
    static final int MAX_MESSAGE = 500;

    public List<String> validate(RePlanRequest req, PlanChanges changes) {
        List<String> errors = new ArrayList<>();
        if (changes == null) {
            return List.of("Response is empty");
        }
        if (changes.coachMessage() == null || changes.coachMessage().isBlank()) {
            errors.add("coachMessage is empty");
        } else if (changes.coachMessage().length() > MAX_MESSAGE) {
            errors.add("coachMessage is longer than " + MAX_MESSAGE + " characters");
        }
        List<TaskChange> list = changes.changes() == null ? List.of() : changes.changes();
        if (list.size() > MAX_CHANGES) {
            errors.add("Too many changes (" + list.size() + "); maximum is " + MAX_CHANGES);
            return errors;
        }

        // Resulting date and minutes per upcoming task id; added tasks get negative keys
        Map<Long, LocalDate> dates = new HashMap<>();
        Map<Long, Integer> minutes = new HashMap<>();
        for (TaskView t : req.upcoming()) {
            dates.put(t.id(), t.date());
            minutes.put(t.id(), t.minutes());
        }
        Set<Long> milestoneIds = new HashSet<>();
        req.milestones().stream().map(MilestoneView::id).forEach(milestoneIds::add);

        long newKey = -1;
        for (int i = 0; i < list.size(); i++) {
            TaskChange c = list.get(i);
            String label = "Change " + (i + 1);
            if (c.action() == null) {
                errors.add(label + " has no action");
                continue;
            }
            switch (c.action()) {
                case MOVE, RESIZE, REMOVE -> {
                    if (c.taskId() == null || !dates.containsKey(c.taskId())) {
                        errors.add(label + " (" + c.action() + ") refers to task id " + c.taskId()
                                + ", which is not an upcoming task");
                        continue;
                    }
                    if (c.action() == PlanChanges.Action.REMOVE) {
                        dates.remove(c.taskId());
                        minutes.remove(c.taskId());
                    } else if (c.action() == PlanChanges.Action.MOVE) {
                        if (checkDate(c.date(), req, label, errors)) dates.put(c.taskId(), c.date());
                    } else if (checkMinutes(c.minutes(), req, label, errors)) {
                        minutes.put(c.taskId(), c.minutes());
                    }
                }
                case ADD -> {
                    if (c.title() == null || c.title().isBlank()) {
                        errors.add(label + " (ADD) has an empty title");
                    } else if (c.title().length() > MAX_TITLE) {
                        errors.add(label + " (ADD) title is longer than " + MAX_TITLE + " characters");
                    }
                    if (c.milestoneId() != null && !milestoneIds.contains(c.milestoneId())) {
                        errors.add(label + " (ADD) refers to unknown milestone id " + c.milestoneId());
                    }
                    boolean dateOk = checkDate(c.date(), req, label, errors);
                    boolean minutesOk = checkMinutes(c.minutes(), req, label, errors);
                    if (dateOk && minutesOk) {
                        dates.put(newKey, c.date());
                        minutes.put(newKey, c.minutes());
                        newKey--;
                    }
                }
            }
        }

        Map<LocalDate, Integer> perDay = new TreeMap<>();
        dates.forEach((id, date) -> {
            if (!date.isBefore(req.from()) && !date.isAfter(req.windowEnd())) {
                perDay.merge(date, minutes.get(id), Integer::sum);
            }
        });
        perDay.forEach((day, total) -> {
            if (total > req.minutesPerDay()) {
                errors.add(day + " would have " + total + " minutes of tasks; the limit is " + req.minutesPerDay());
            }
        });
        return errors;
    }

    private static boolean checkDate(LocalDate date, RePlanRequest req, String label, List<String> errors) {
        if (date == null) {
            errors.add(label + " has no date");
            return false;
        }
        if (date.isBefore(req.from()) || date.isAfter(req.windowEnd())) {
            errors.add(label + " date " + date + " must be between " + req.from() + " and " + req.windowEnd());
            return false;
        }
        return true;
    }

    private static boolean checkMinutes(Integer value, RePlanRequest req, String label, List<String> errors) {
        if (value == null || value < 5 || value > req.minutesPerDay()) {
            errors.add(label + " minutes " + value + " must be 5-" + req.minutesPerDay());
            return false;
        }
        return true;
    }
}
