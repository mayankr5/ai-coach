package com.aicoach.goal;

import com.aicoach.common.BadRequestException;
import com.aicoach.common.NotFoundException;
import com.aicoach.common.PageResponse;
import com.aicoach.goal.GoalDtos.*;
import com.aicoach.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private final GoalRepository goals;
    private final UserRepository users;

    public GoalService(GoalRepository goals, UserRepository users) {
        this.goals = goals;
        this.users = users;
    }

    @Transactional
    public GoalResponse create(Long userId, CreateGoalRequest req) {
        Goal goal = new Goal();
        goal.setUser(users.getReferenceById(userId));
        goal.setTitle(req.title());
        goal.setDescription(req.description());
        goal.setDeadline(req.deadline());
        goal.setMinutesPerDay(req.minutesPerDay());
        goal.setExtraMinutesPerDay(req.extraMinutesPerDay() == null ? 0 : req.extraMinutesPerDay());
        goal.setExperienceLevel(req.experienceLevel());
        checkExtraMinutes(goal);
        return GoalResponse.from(goals.save(goal));
    }

    @Transactional(readOnly = true)
    public PageResponse<GoalSummary> list(Long userId, GoalStatus status, Pageable pageable) {
        Page<Goal> page = status == null
                ? goals.findByUserId(userId, pageable)
                : goals.findByUserIdAndStatus(userId, status, pageable);
        return PageResponse.of(page, GoalSummary::from);
    }

    @Transactional(readOnly = true)
    public GoalResponse get(Long userId, Long goalId) {
        return GoalResponse.from(findOwned(userId, goalId));
    }

    @Transactional
    public GoalResponse update(Long userId, Long goalId, UpdateGoalRequest req) {
        Goal goal = findOwned(userId, goalId);
        if (req.title() != null) goal.setTitle(req.title());
        if (req.description() != null) goal.setDescription(req.description());
        if (req.deadline() != null) goal.setDeadline(req.deadline());
        if (req.minutesPerDay() != null) goal.setMinutesPerDay(req.minutesPerDay());
        if (req.extraMinutesPerDay() != null) goal.setExtraMinutesPerDay(req.extraMinutesPerDay());
        if (req.experienceLevel() != null) goal.setExperienceLevel(req.experienceLevel());
        if (req.status() != null) goal.setStatus(req.status());
        checkExtraMinutes(goal);
        return GoalResponse.from(goal);
    }

    @Transactional
    public void delete(Long userId, Long goalId) {
        goals.delete(findOwned(userId, goalId));
    }

    /** Catch-up days stay realistic: users tend to overestimate how much extra they can do. */
    private static void checkExtraMinutes(Goal goal) {
        int max = goal.getMinutesPerDay() / 2;
        if (goal.getExtraMinutesPerDay() > max) {
            throw new BadRequestException("extraMinutesPerDay can be at most half of minutesPerDay (" + max + ")");
        }
    }

    /** Used by other features (tasks, check-ins) to enforce ownership the same way. */
    @Transactional(readOnly = true)
    public Goal findOwned(Long userId, Long goalId) {
        return goals.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new NotFoundException("Goal not found"));
    }
}
