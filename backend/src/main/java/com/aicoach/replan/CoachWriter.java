package com.aicoach.replan;

import com.aicoach.ai.AiResult;

/**
 * Writes the coach message for a re-plan that has already been computed. The writer only explains;
 * it can't change the schedule, so a weak model can't break the plan.
 */
public interface CoachWriter {

    /**
     * @return the message and the model that wrote it
     * @throws com.aicoach.plan.AiUnavailableException if no message could be produced
     */
    AiResult<String> write(RePlanRequest request, CatchUpPlan plan);
}
