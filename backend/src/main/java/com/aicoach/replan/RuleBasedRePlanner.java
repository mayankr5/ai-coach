package com.aicoach.replan;

import com.aicoach.replan.PlanChanges.Action;
import com.aicoach.replan.PlanChanges.TaskChange;
import com.aicoach.replan.RePlanRequest.TaskView;
import com.aicoach.task.TaskStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic fallback used when the AI is unavailable and by the nightly job: re-schedules
 * missed work on the earliest days with free time. It never edits existing tasks.
 */
@Component
public class RuleBasedRePlanner {

    public PlanChanges replan(RePlanRequest req) {
        Map<LocalDate, Integer> used = new HashMap<>();
        req.upcoming().forEach(t -> used.merge(t.date(), t.minutes(), Integer::sum));

        List<TaskChange> changes = new ArrayList<>();
        int unplaced = 0;
        for (TaskView missed : req.missed()) {
            boolean partial = missed.status() == TaskStatus.PARTIAL;
            String title = partial ? "Finish: " + missed.title() : missed.title();
            int minutes = Math.min(req.minutesPerDay(), partial ? Math.max(5, missed.minutes() / 2) : missed.minutes());

            LocalDate day = firstDayWithRoom(req, used, minutes);
            if (day == null) {
                unplaced++;
                continue;
            }
            used.merge(day, minutes, Integer::sum);
            changes.add(new TaskChange(Action.ADD, null, title, day, minutes, missed.milestoneId()));
        }
        return new PlanChanges(message(changes.size(), unplaced), changes);
    }

    private static LocalDate firstDayWithRoom(RePlanRequest req, Map<LocalDate, Integer> used, int minutes) {
        for (LocalDate d = req.from(); !d.isAfter(req.deadline()); d = d.plusDays(1)) {
            if (used.getOrDefault(d, 0) + minutes <= req.minutesPerDay()) {
                return d;
            }
        }
        return null;
    }

    private static String message(int placed, int unplaced) {
        if (placed == 0 && unplaced == 0) {
            return "Nothing to catch up on. Keep going with the plan.";
        }
        StringBuilder sb = new StringBuilder();
        if (placed > 0) {
            sb.append("Moved ").append(placed).append(placed == 1 ? " unfinished task" : " unfinished tasks")
                    .append(" to the next days with free time.");
        }
        if (unplaced > 0) {
            sb.append(sb.isEmpty() ? "" : " ").append(unplaced)
                    .append(unplaced == 1 ? " task doesn't" : " tasks don't")
                    .append(" fit before the deadline; consider extending it or adding daily time.");
        }
        return sb.toString();
    }
}
