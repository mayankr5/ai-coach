package com.aicoach.replan;

import com.aicoach.checkin.CheckInRepository;
import com.aicoach.goal.Goal;
import com.aicoach.goal.GoalRepository;
import com.aicoach.goal.GoalStatus;
import com.aicoach.task.Task;
import com.aicoach.task.TaskRepository;
import com.aicoach.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Nightly: for every active goal with no check-in yesterday (in the user's timezone), marks
 * overdue pending tasks as skipped and re-plans with the rule-based planner, so a forgotten
 * check-in doesn't leave the plan stuck in the past.
 */
@Component
public class MissedDayJob {

    private static final Logger log = LoggerFactory.getLogger(MissedDayJob.class);

    private final GoalRepository goals;
    private final TaskRepository tasks;
    private final CheckInRepository checkIns;
    private final ReplanService replanService;
    private final TransactionTemplate tx;

    public MissedDayJob(GoalRepository goals, TaskRepository tasks, CheckInRepository checkIns,
                        ReplanService replanService, TransactionTemplate tx) {
        this.goals = goals;
        this.tasks = tasks;
        this.checkIns = checkIns;
        this.replanService = replanService;
        this.tx = tx;
    }

    @Scheduled(cron = "${app.planning.missed-day-cron}", zone = "UTC")
    public void run() {
        List<Long> goalIds = tx.execute(s -> goals.findByStatus(GoalStatus.ACTIVE).stream().map(Goal::getId).toList());
        int replanned = 0;
        for (Long goalId : goalIds) {
            try {
                if (processGoal(goalId)) replanned++;
            } catch (RuntimeException e) {
                // One bad goal must not stop the job for everyone else
                log.error("Missed-day processing failed for goal {}", goalId, e);
            }
        }
        log.info("Missed-day job checked {} goals, re-planned {}", goalIds.size(), replanned);
    }

    private boolean processGoal(Long goalId) {
        LocalDate today = tx.execute(s -> {
            Goal goal = goals.findById(goalId).orElseThrow();
            LocalDate userToday = LocalDate.now(ZoneId.of(goal.getUser().getTimezone()));
            LocalDate yesterday = userToday.minusDays(1);
            if (checkIns.existsByGoalIdAndCheckInDate(goalId, yesterday)) {
                return null;
            }
            List<Task> overdue = tasks.findOverduePending(goalId, yesterday);
            if (overdue.isEmpty()) {
                return null;
            }
            overdue.forEach(t -> t.setStatus(TaskStatus.SKIPPED));
            return userToday;
        });
        if (today == null) {
            return false;
        }
        replanService.replan(goalId, today, null, false);
        return true;
    }
}
