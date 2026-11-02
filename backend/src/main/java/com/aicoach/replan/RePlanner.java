package com.aicoach.replan;

import java.util.List;

/** Proposes changes to a goal's upcoming tasks. Implementations must not touch the database. */
public interface RePlanner {

    /**
     * @param previousErrors validation errors from the last attempt, empty on the first try
     */
    PlanChanges replan(RePlanRequest request, List<String> previousErrors);
}
