package com.aicoach.plan;

public enum RevisionSource {
    /** First plan from {@link PlanService}. */
    INITIAL,
    /** AI re-plan after a check-in. */
    AI,
    /** Rule-based re-plan because the AI failed or returned invalid changes. */
    FALLBACK,
    /** Rule-based re-plan by the nightly job for a day with no check-in. */
    NIGHTLY
}
