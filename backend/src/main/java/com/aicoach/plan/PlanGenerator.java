package com.aicoach.plan;

import java.util.List;

/** Turns a goal into a plan. Implementations must not touch the database. */
public interface PlanGenerator {

    /**
     * @param previousErrors validation errors from the last attempt, empty on the first try,
     *                       so the generator can correct its output
     */
    GeneratedPlan generate(PlanRequest request, List<String> previousErrors);
}
