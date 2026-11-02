package com.aicoach.checkin;

import com.aicoach.checkin.CheckInDtos.*;
import com.aicoach.common.BadRequestException;
import com.aicoach.common.PageResponse;
import com.aicoach.goal.Goal;
import com.aicoach.goal.GoalService;
import com.aicoach.goal.GoalStatus;
import com.aicoach.replan.ReplanService;
import com.aicoach.replan.ReplanService.ReplanResult;
import com.aicoach.task.Task;
import com.aicoach.task.TaskDtos.TaskResponse;
import com.aicoach.task.TaskRepository;
import com.aicoach.task.TaskStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CheckInService {

    private final GoalService goalService;
    private final TaskRepository tasks;
    private final CheckInRepository checkIns;
    private final ReplanService replanService;
    private final TransactionTemplate tx;
    private final int taskWindowDays;

    public CheckInService(GoalService goalService, TaskRepository tasks, CheckInRepository checkIns,
                          ReplanService replanService, TransactionTemplate tx,
                          @Value("${app.planning.task-window-days}") int taskWindowDays) {
        this.goalService = goalService;
        this.tasks = tasks;
        this.checkIns = checkIns;
        this.replanService = replanService;
        this.tx = tx;
        this.taskWindowDays = taskWindowDays;
    }

    /**
     * Records the day's results, then re-plans. The check-in is committed before the AI call,
     * so it is never lost even if re-planning fails.
     */
    public CheckInResponse submit(Long userId, Long goalId, CheckInRequest req) {
        Long checkInId = tx.execute(status -> record(goalService.findOwned(userId, goalId), req));

        LocalDate from = req.date().plusDays(1);
        ReplanResult result = replanService.replan(goalId, from, req.note(), true);

        return tx.execute(status -> {
            CheckIn checkIn = checkIns.findById(checkInId).orElseThrow();
            checkIn.setCoachMessage(result.coachMessage());
            List<TaskResponse> upcoming = tasks.findByGoalIdAndScheduledDateBetween(
                            goalId, from, from.plusDays(taskWindowDays - 1)).stream()
                    .sorted((a, b) -> a.getScheduledDate().equals(b.getScheduledDate())
                            ? a.getId().compareTo(b.getId()) : a.getScheduledDate().compareTo(b.getScheduledDate()))
                    .map(TaskResponse::from)
                    .toList();
            return new CheckInResponse(checkIn.getId(), checkIn.getCheckInDate(), checkIn.getNote(),
                    result.coachMessage(), result.source(), upcoming);
        });
    }

    private Long record(Goal goal, CheckInRequest req) {
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new BadRequestException("Only active goals can be checked in");
        }
        LocalDate today = LocalDate.now(ZoneId.of(goal.getUser().getTimezone()));
        if (!req.date().equals(today) && !req.date().equals(today.minusDays(1))) {
            throw new BadRequestException("Check-ins are only allowed for today (" + today + ") or yesterday");
        }

        List<Long> ids = req.tasks().stream().map(TaskResult::taskId).distinct().toList();
        if (ids.size() != req.tasks().size()) {
            throw new BadRequestException("Each task may appear only once");
        }
        Map<Long, Task> found = tasks.findByGoalIdAndIdIn(goal.getId(), ids).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
        for (TaskResult r : req.tasks()) {
            Task task = found.get(r.taskId());
            if (task == null) {
                throw new BadRequestException("Task " + r.taskId() + " is not part of this goal");
            }
            if (task.getScheduledDate().isAfter(req.date())) {
                throw new BadRequestException("Task " + r.taskId() + " is scheduled after " + req.date());
            }
            if (r.status() == TaskStatus.PENDING) {
                throw new BadRequestException("Task " + r.taskId() + ": report DONE, PARTIAL or SKIPPED");
            }
            if (task.getStatus() != r.status()) {
                task.setStatus(r.status());
                // A changed result is new information for the re-planner
                task.setCarriedOver(false);
            }
        }

        // One check-in per goal per day; submitting again updates it
        CheckIn checkIn = checkIns.findByGoalIdAndCheckInDate(goal.getId(), req.date()).orElseGet(() -> {
            CheckIn c = new CheckIn();
            c.setGoal(goal);
            c.setCheckInDate(req.date());
            return c;
        });
        checkIn.setNote(req.note());
        checkIn.setUpdatedAt(Instant.now());
        return checkIns.save(checkIn).getId();
    }

    @Transactional(readOnly = true)
    public PageResponse<CheckInSummary> history(Long userId, Long goalId, Pageable pageable) {
        Goal goal = goalService.findOwned(userId, goalId);
        return PageResponse.of(checkIns.findByGoalId(goal.getId(), pageable), CheckInSummary::from);
    }
}
