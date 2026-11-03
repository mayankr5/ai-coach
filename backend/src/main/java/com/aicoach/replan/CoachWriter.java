package com.aicoach.replan;

/**
 * Writes the coach message for a re-plan that has already been computed. The writer only explains;
 * it can't change the schedule, so a weak model can't break the plan.
 */
public interface CoachWriter {

    /** @throws com.aicoach.plan.AiUnavailableException if no message could be produced */
    String write(RePlanRequest request, CatchUpPlan plan);
}
