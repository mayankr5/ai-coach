package com.aicoach.plan;

import com.aicoach.plan.PlanDtos.PlanResponse;
import com.aicoach.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/goals/{goalId}/plan")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    public PlanResponse generate(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId) {
        return planService.generate(user.id(), goalId);
    }
}
