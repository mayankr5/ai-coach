package com.aicoach.plan;

import com.aicoach.plan.GeneratedPlan.PlannedMilestone;
import com.aicoach.plan.GeneratedPlan.PlannedTask;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fixes harmless mistakes before validation instead of rejecting the whole plan. Today that
 * means dropping tasks scheduled outside the task window: the model planned too far ahead,
 * and later re-plans will schedule that work anyway. Real problems are left for the validator.
 */
@Component
public class PlanRepairer {

    public record Repaired(GeneratedPlan plan, int droppedTasks) {
    }

    public Repaired repair(GeneratedPlan plan, PlanRequest req) {
        if (plan == null || plan.milestones() == null) {
            return new Repaired(plan, 0);
        }
        int dropped = 0;
        List<PlannedMilestone> milestones = new java.util.ArrayList<>();
        for (PlannedMilestone m : plan.milestones()) {
            if (m == null) {
                continue;
            }
            List<PlannedTask> kept = new java.util.ArrayList<>();
            for (PlannedTask t : m.tasks() == null ? List.<PlannedTask>of() : m.tasks()) {
                boolean outside = t != null && t.date() != null
                        && (t.date().isBefore(req.today()) || t.date().isAfter(req.taskWindowEnd()));
                if (t == null || outside) {
                    dropped++;
                } else {
                    kept.add(t);
                }
            }
            milestones.add(new PlannedMilestone(m.title(), m.targetDate(), kept));
        }
        return new Repaired(new GeneratedPlan(milestones), dropped);
    }
}
