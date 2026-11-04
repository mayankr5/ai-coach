package com.aicoach.checkin;

import com.aicoach.checkin.ProgressService.Health;
import com.aicoach.goal.Goal;
import com.aicoach.goal.Milestone;
import com.aicoach.task.Task;
import com.aicoach.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressHealthTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    final Goal goal = new Goal();
    final Milestone first = milestone(0, TODAY.plusDays(4));
    final Milestone later = milestone(1, TODAY.plusDays(30));

    ProgressHealthTest() {
        goal.setMinutesPerDay(60);
    }

    @Test
    void workThatFitsTheTimeLeftIsOnTrack() {
        // 5 days x 60 = 300 minutes of time, 300 minutes of work
        List<Task> tasks = tasks(first, 5, 60);

        assertThat(ProgressService.health(goal, tasks, first, TODAY)).isEqualTo(new Health(100, 0));
    }

    @Test
    void workBeyondTheTimeLeftShowsMinutesBehind() {
        List<Task> tasks = tasks(first, 5, 60);
        tasks.add(task(first, TODAY.plusDays(4), 60, TaskStatus.PENDING));

        // 300 of 360 minutes fit: 83%, 60 minutes behind
        assertThat(ProgressService.health(goal, tasks, first, TODAY)).isEqualTo(new Health(83, 60));
    }

    @Test
    void onlyPendingWorkUpToTheNextMilestoneCounts() {
        List<Task> tasks = tasks(first, 5, 60);
        tasks.add(task(first, TODAY.minusDays(1), 60, TaskStatus.DONE));
        tasks.add(task(first, TODAY, 60, TaskStatus.SKIPPED));
        tasks.add(task(later, TODAY.plusDays(4), 60, TaskStatus.PENDING));

        assertThat(ProgressService.health(goal, tasks, first, TODAY)).isEqualTo(new Health(100, 0));
    }

    private List<Task> tasks(Milestone milestone, int days, int minutes) {
        List<Task> tasks = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            tasks.add(task(milestone, TODAY.plusDays(d), minutes, TaskStatus.PENDING));
        }
        return tasks;
    }

    private static Task task(Milestone milestone, LocalDate date, int minutes, TaskStatus status) {
        Task t = new Task();
        t.setMilestone(milestone);
        t.setScheduledDate(date);
        t.setEstimatedMinutes(minutes);
        t.setStatus(status);
        return t;
    }

    private static Milestone milestone(int position, LocalDate target) {
        Milestone m = new Milestone();
        m.setPosition(position);
        m.setTargetDate(target);
        return m;
    }
}
