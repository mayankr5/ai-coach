package com.aicoach.plan;

import com.aicoach.common.BadRequestException;
import com.aicoach.common.ConflictException;
import com.aicoach.goal.Goal;
import com.aicoach.goal.GoalService;
import com.aicoach.goal.Milestone;
import com.aicoach.plan.GeneratedPlan.PlannedMilestone;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
import com.aicoach.plan.PlanDtos.*;
import com.aicoach.task.Task;
import com.aicoach.task.TaskRepository;
import com.aicoach.task.TaskStatus;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);
    static final int MAX_ATTEMPTS = 2;

    private final GoalService goalService;
    private final TaskRepository tasks;
    private final PlanRevisionRepository revisions;
    private final PlanGenerator generator;
    private final PlanValidator validator;
    private final TransactionTemplate tx;
    private final EntityManager em;
    private final int taskWindowDays;

    public PlanService(GoalService goalService, TaskRepository tasks, PlanRevisionRepository revisions,
                       PlanGenerator generator, PlanValidator validator, TransactionTemplate tx,
                       EntityManager em, @Value("${app.planning.task-window-days}") int taskWindowDays) {
        this.goalService = goalService;
        this.tasks = tasks;
        this.revisions = revisions;
        this.generator = generator;
        this.validator = validator;
        this.tx = tx;
        this.em = em;
        this.taskWindowDays = taskWindowDays;
    }

    /**
     * Generates (or regenerates) a goal's plan. The AI call runs outside any transaction so a
     * slow model doesn't hold a database connection; only the final save is transactional.
     */
    public PlanResponse generate(Long userId, Long goalId) {
        PlanRequest request = tx.execute(status -> buildRequest(goalService.findOwned(userId, goalId)));

        List<String> errors = List.of();
        GeneratedPlan plan = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            plan = generator.generate(request, errors);
            errors = validator.validate(plan, request);
            if (errors.isEmpty()) {
                break;
            }
            log.warn("Plan attempt {} for goal {} rejected: {}", attempt, goalId, errors);
        }
        if (!errors.isEmpty()) {
            throw new AiUnavailableException("AI returned an invalid plan after " + MAX_ATTEMPTS
                    + " attempts: " + String.join("; ", errors));
        }

        GeneratedPlan validPlan = plan;
        return tx.execute(status -> save(goalService.findOwned(userId, goalId), validPlan));
    }

    private PlanRequest buildRequest(Goal goal) {
        ensureNoProgress(goal);
        LocalDate today = LocalDate.now(ZoneId.of(goal.getUser().getTimezone()));
        if (goal.getDeadline().isBefore(today)) {
            throw new BadRequestException("Goal deadline has passed; update it before planning");
        }
        LocalDate windowEnd = today.plusDays(taskWindowDays - 1);
        if (windowEnd.isAfter(goal.getDeadline())) {
            windowEnd = goal.getDeadline();
        }
        return new PlanRequest(goal.getTitle(), goal.getDescription(), goal.getExperienceLevel(),
                goal.getMinutesPerDay(), goal.getDeadline(), today, windowEnd);
    }

    private PlanResponse save(Goal goal, GeneratedPlan plan) {
        // Re-checked here: the user may have completed a task while the AI was thinking
        ensureNoProgress(goal);
        tasks.deleteByGoalId(goal.getId());
        goal.getMilestones().clear();
        em.flush();

        List<Task> newTasks = new ArrayList<>();
        for (int i = 0; i < plan.milestones().size(); i++) {
            PlannedMilestone pm = plan.milestones().get(i);
            Milestone milestone = new Milestone();
            milestone.setGoal(goal);
            milestone.setTitle(pm.title().strip());
            milestone.setTargetDate(pm.targetDate());
            milestone.setPosition(i);
            goal.getMilestones().add(milestone);

            for (PlannedTask pt : pm.tasks() == null ? List.<PlannedTask>of() : pm.tasks()) {
                Task task = new Task();
                task.setGoal(goal);
                task.setMilestone(milestone);
                task.setTitle(pt.title().strip());
                task.setScheduledDate(pt.date());
                task.setEstimatedMinutes(pt.minutes());
                newTasks.add(task);
            }
        }
        em.flush();
        tasks.saveAll(newTasks);

        PlanRevision revision = new PlanRevision();
        revision.setGoal(goal);
        revision.setSource(RevisionSource.INITIAL);
        revision.setSummary("Initial plan: " + plan.milestones().size() + " milestones, "
                + newTasks.size() + " tasks");
        revisions.save(revision);

        return toResponse(goal, newTasks);
    }

    private void ensureNoProgress(Goal goal) {
        if (tasks.existsByGoalIdAndStatusNot(goal.getId(), TaskStatus.PENDING)) {
            throw new ConflictException("Goal already has progress; the plan is adjusted by daily check-ins");
        }
    }

    private static PlanResponse toResponse(Goal goal, List<Task> goalTasks) {
        Map<Milestone, List<PlanTask>> byMilestone = goalTasks.stream().collect(Collectors.groupingBy(
                Task::getMilestone,
                Collectors.mapping(t -> new PlanTask(t.getId(), t.getTitle(), t.getScheduledDate(),
                        t.getEstimatedMinutes(), t.getStatus()), Collectors.toList())));
        List<MilestonePlan> milestones = goal.getMilestones().stream()
                .map(m -> new MilestonePlan(m.getId(), m.getTitle(), m.getTargetDate(), m.getPosition(),
                        byMilestone.getOrDefault(m, List.of())))
                .toList();
        return new PlanResponse(goal.getId(), milestones);
    }
}
