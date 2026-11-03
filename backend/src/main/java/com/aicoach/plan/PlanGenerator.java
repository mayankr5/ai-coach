package com.aicoach.plan;

import com.aicoach.ai.AiResult;

/** Turns a goal into a plan. Implementations must not touch the database. */
public interface PlanGenerator {

    /**
     * @return a plan that passes {@link PlanValidator}, and the model that made it
     * @throws AiUnavailableException if no usable plan could be produced
     */
    AiResult<GeneratedPlan> generate(PlanRequest request);
}
