package com.aicoach.replan;

import com.aicoach.replan.PlanChanges.Action;
import com.aicoach.replan.PlanChanges.TaskChange;
import com.aicoach.replan.RePlanRequest.TaskView;
import com.aicoach.task.TaskStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Deterministic re-scheduling of missed work. Tasks are a series, so leftover work goes first,
 * on the next day, and later tasks slide forward in order instead of the leftover landing weeks later.
 * <ul>
 *   <li>A day holds up to minutesPerDay; while catching up it may use the goal's extra minutes,
 *       but never on more than {@value #MAX_EXTRA_DAYS_IN_A_ROW} days in a row.</li>
 *   <li>Tasks only ever move later, never earlier, and whole tasks move (no splitting).</li>
 *   <li>A task too big for any day gets a day to itself.</li>
 *   <li>A catch-up task takes the sequence number right after the task it continues, so it is listed
 *       first on its day and stays ahead of later work in future re-plans.</li>
 *   <li>Work that can't fit before the deadline is put on the deadline and reported as overflow.</li>
 * </ul>
 * With nothing missed, the plan is returned unchanged.
 */
@Component
public class CatchUpScheduler {

    static final int MAX_EXTRA_DAYS_IN_A_ROW = 2;

    private static final Comparator<TaskView> SERIES = Comparator.comparingInt(TaskView::sequence)
            .thenComparing(TaskView::id);

    private record Item(TaskView task, TaskChange followUp, LocalDate earliest) {
        int minutes() {
            return followUp != null ? followUp.minutes() : task.minutes();
        }
    }

    public CatchUpPlan schedule(RePlanRequest req) {
        int normal = req.minutesPerDay();
        int withExtra = normal + req.extraMinutesPerDay();

        List<Item> queue = new ArrayList<>();
        req.missed().stream()
                .sorted(SERIES)
                .forEach(missed -> queue.add(new Item(null, followUp(missed, normal), req.from())));
        req.upcoming().stream()
                .sorted(SERIES)
                .forEach(t -> queue.add(new Item(t, null, t.date())));

        Map<LocalDate, Integer> used = new HashMap<>();
        Set<LocalDate> extraDays = new TreeSet<>();
        List<TaskChange> changes = new ArrayList<>();
        List<TaskChange> followUps = new ArrayList<>();
        int shifted = 0;
        int overflow = 0;

        LocalDate cursor = req.from();
        boolean behind = !req.missed().isEmpty();
        for (Item item : queue) {
            if (cursor.isBefore(item.earliest())) {
                // Jumping to a day nothing has been pushed onto yet: from here the original plan holds
                cursor = item.earliest();
                behind = false;
            }
            LocalDate day = cursor;
            if (behind) {
                while (!day.isAfter(req.deadline()) && !fits(day, item.minutes(), used, extraDays, normal, withExtra)) {
                    day = day.plusDays(1);
                }
                if (day.isAfter(req.deadline())) {
                    day = req.deadline();
                    overflow++;
                }
            }
            int total = used.merge(day, item.minutes(), Integer::sum);
            if (behind && total > normal && total <= withExtra) {
                extraDays.add(day);
            }
            cursor = day;

            if (item.followUp() != null) {
                TaskChange add = new TaskChange(Action.ADD, null, item.followUp().title(), day,
                        item.minutes(), item.followUp().milestoneId(), item.followUp().sequence());
                changes.add(add);
                followUps.add(add);
            } else if (!day.equals(item.task().date())) {
                changes.add(new TaskChange(Action.MOVE, item.task().id(), null, day, null, null, null));
                shifted++;
            }
        }
        return new CatchUpPlan(changes, followUps, shifted, List.copyOf(extraDays), overflow);
    }

    private static boolean fits(LocalDate day, int minutes, Map<LocalDate, Integer> used, Set<LocalDate> extraDays,
                                int normal, int withExtra) {
        int dayUsed = used.getOrDefault(day, 0);
        if (dayUsed == 0 || dayUsed + minutes <= normal) {
            return true;
        }
        boolean extraStreakFull = true;
        for (int i = 1; i <= MAX_EXTRA_DAYS_IN_A_ROW; i++) {
            extraStreakFull &= extraDays.contains(day.minusDays(i));
        }
        return dayUsed + minutes <= withExtra && (extraDays.contains(day) || !extraStreakFull);
    }

    /** Partly done work needs about half the time again; skipped work needs all of it. */
    private static TaskChange followUp(TaskView missed, int normal) {
        boolean partial = missed.status() == TaskStatus.PARTIAL;
        String title = partial ? "Finish: " + missed.title() : missed.title();
        int minutes = Math.min(normal, partial ? Math.max(5, missed.minutes() / 2) : missed.minutes());
        return new TaskChange(Action.ADD, null, title, null, minutes, missed.milestoneId(), missed.sequence() + 1);
    }
}
