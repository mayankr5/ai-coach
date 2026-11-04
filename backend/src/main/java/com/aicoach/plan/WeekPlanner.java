package com.aicoach.plan;

import com.aicoach.ai.AiResult;

/** Plans one more week of tasks for a goal. Implementations must not touch the database. */
public interface WeekPlanner {

    /**
     * @return tasks that pass {@link PlanValidator#validateWeek}, and the model that made them
     * @throws AiUnavailableException if no usable week could be produced
     */
    AiResult<PlannedWeek> plan(WeekRequest request);
}
