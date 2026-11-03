package com.aicoach.replan;

import com.aicoach.goal.ExperienceLevel;
import com.aicoach.replan.PlanChanges.Action;
import com.aicoach.replan.PlanChanges.TaskChange;
import com.aicoach.replan.RePlanRequest.TaskView;
import com.aicoach.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class CatchUpSchedulerTest {

    static final LocalDate FROM = LocalDate.of(2026, 11, 4);
    static final LocalDate FAR = FROM.plusDays(60);

    final CatchUpScheduler scheduler = new CatchUpScheduler();

    @Test
    void leftoverGoesOnNextDayUsingExtraTime() {
        // The real case: 48 of 60 minutes planned per day, half of a 48-minute task left, +15 extra allowed
        RePlanRequest req = request(15, FAR, daily(3, 48),
                List.of(missed(100L, "Install Spring Boot", 48, TaskStatus.PARTIAL)));

        CatchUpPlan plan = scheduler.schedule(req);

        assertThat(plan.changes()).containsExactly(
                new TaskChange(Action.ADD, null, "Finish: Install Spring Boot", FROM, 24, null));
        assertThat(plan.extraDays()).containsExactly(FROM);
        assertThat(plan.shifted()).isZero();
        assertThat(perDay(req, plan)).containsEntry(FROM, 72);
    }

    @Test
    void withoutExtraTimeLaterTasksShiftForwardInOrder() {
        List<TaskView> upcoming = daily(3, 48);
        RePlanRequest req = request(0, FAR, upcoming,
                List.of(missed(100L, "Install Spring Boot", 48, TaskStatus.PARTIAL)));

        CatchUpPlan plan = scheduler.schedule(req);

        // Leftover first, on the next day; every later task slides one day
        assertThat(plan.followUps()).extracting(TaskChange::date).containsExactly(FROM);
        assertThat(plan.shifted()).isEqualTo(3);
        assertThat(newDates(req, plan)).containsExactly(FROM.plusDays(1), FROM.plusDays(2), FROM.plusDays(3));
        assertThat(perDay(req, plan).values()).allMatch(total -> total <= 60);
        assertThat(plan.extraDays()).isEmpty();
    }

    @Test
    void extraTimeIsNotUsedMoreThanTwoDaysInARow() {
        // Three 20-minute tasks a day (60), +20 extra, and a whole 60-minute day was skipped
        List<TaskView> upcoming = new ArrayList<>();
        long id = 1;
        for (int d = 0; d < 5; d++) {
            for (int i = 0; i < 3; i++) {
                upcoming.add(new TaskView(id++, "Task " + d + "." + i, FROM.plusDays(d), 20, null, TaskStatus.PENDING));
            }
        }
        RePlanRequest req = request(20, FAR, upcoming, List.of(missed(100L, "Skipped day", 60, TaskStatus.SKIPPED)));

        CatchUpPlan plan = scheduler.schedule(req);

        assertThat(plan.extraDays()).containsExactly(FROM, FROM.plusDays(1), FROM.plusDays(3));
        assertThat(perDay(req, plan)).containsExactly(
                Map.entry(FROM, 80), Map.entry(FROM.plusDays(1), 80), Map.entry(FROM.plusDays(2), 60),
                Map.entry(FROM.plusDays(3), 80), Map.entry(FROM.plusDays(4), 60));
        assertOrderKept(req, plan);
    }

    @Test
    void nothingMissedLeavesPlanUnchanged() {
        // Even a day that is over the limit is the user's choice; nothing is moved without missed work
        RePlanRequest req = request(0, FAR, List.of(
                new TaskView(1L, "Long session", FROM, 50, null, TaskStatus.PENDING),
                new TaskView(2L, "Extra reading", FROM, 30, null, TaskStatus.PENDING)), List.of());

        CatchUpPlan plan = scheduler.schedule(req);

        assertThat(plan.isEmpty()).isTrue();
        assertThat(ReplanService.defaultMessage(plan)).contains("Nothing to catch up on");
    }

    @Test
    void workPastTheDeadlineIsReportedNotLost() {
        RePlanRequest req = request(0, FROM, List.of(new TaskView(1L, "Full day", FROM, 60, null, TaskStatus.PENDING)),
                List.of(missed(100L, "Generics", 30, TaskStatus.SKIPPED)));

        CatchUpPlan plan = scheduler.schedule(req);

        assertThat(plan.followUps()).extracting(TaskChange::date).containsExactly(FROM);
        assertThat(plan.overflow()).isEqualTo(1);
        assertThat(ReplanService.defaultMessage(plan)).contains("1 task doesn't fit before the deadline");
    }

    @Test
    void taskBiggerThanADayGetsADayToItself() {
        RePlanRequest req = request(0, FAR, List.of(
                        new TaskView(1L, "Build project", FROM, 90, null, TaskStatus.PENDING),
                        new TaskView(2L, "Write tests", FROM.plusDays(1), 30, null, TaskStatus.PENDING)),
                List.of(missed(100L, "Setup", 20, TaskStatus.SKIPPED)));

        CatchUpPlan plan = scheduler.schedule(req);

        assertThat(newDates(req, plan)).containsExactly(FROM.plusDays(1), FROM.plusDays(2));
        assertThat(ReplanService.defaultMessage(plan))
                .isEqualTo("Added 1 catch-up task starting " + FROM + ". Moved 2 later tasks forward to keep the order.");
    }

    private static List<TaskView> daily(int days, int minutes) {
        List<TaskView> tasks = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            tasks.add(new TaskView((long) d + 1, "Day " + d, FROM.plusDays(d), minutes, null, TaskStatus.PENDING));
        }
        return tasks;
    }

    private static TaskView missed(long id, String title, int minutes, TaskStatus status) {
        return new TaskView(id, title, FROM.minusDays(1), minutes, null, status);
    }

    /** Dates of the upcoming tasks after applying the plan, in their original order. */
    private static List<LocalDate> newDates(RePlanRequest req, CatchUpPlan plan) {
        Map<Long, LocalDate> moved = new HashMap<>();
        plan.changes().stream().filter(c -> c.action() == Action.MOVE).forEach(c -> moved.put(c.taskId(), c.date()));
        return req.upcoming().stream().map(t -> moved.getOrDefault(t.id(), t.date())).toList();
    }

    private static void assertOrderKept(RePlanRequest req, CatchUpPlan plan) {
        List<LocalDate> dates = newDates(req, plan);
        assertThat(dates).isSorted();
        assertThat(plan.followUps().getLast().date()).isBeforeOrEqualTo(dates.getFirst());
    }

    private static Map<LocalDate, Integer> perDay(RePlanRequest req, CatchUpPlan plan) {
        Map<LocalDate, Integer> totals = new TreeMap<>();
        List<LocalDate> dates = newDates(req, plan);
        for (int i = 0; i < dates.size(); i++) {
            totals.merge(dates.get(i), req.upcoming().get(i).minutes(), Integer::sum);
        }
        plan.followUps().forEach(f -> totals.merge(f.date(), f.minutes(), Integer::sum));
        return totals;
    }

    private static RePlanRequest request(int extra, LocalDate deadline, List<TaskView> upcoming, List<TaskView> missed) {
        return new RePlanRequest(1L, "Learn Java", null, ExperienceLevel.BEGINNER, 60, extra, deadline, FROM,
                FROM.plusDays(13), null, null, upcoming, missed, List.of());
    }
}
