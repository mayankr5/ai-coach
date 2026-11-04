package com.aicoach.plan;

import com.aicoach.plan.PlanDtos.ExtensionResponse;
import com.aicoach.plan.PlanDtos.PlanResponse;
import com.aicoach.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/goals/{goalId}/plan")
public class PlanController {

    private final PlanService planService;
    private final PlanExtender planExtender;

    public PlanController(PlanService planService, PlanExtender planExtender) {
        this.planService = planService;
        this.planExtender = planExtender;
    }

    @GetMapping
    public PlanResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId) {
        return planService.get(user.id(), goalId);
    }

    @PostMapping
    public PlanResponse generate(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId) {
        return planService.generate(user.id(), goalId);
    }

    /** Plans the week after the last planned task; normally the nightly job does this when the plan runs low. */
    @PostMapping("/extend")
    public ExtensionResponse extend(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId) {
        return planExtender.extendNow(user.id(), goalId);
    }
}
