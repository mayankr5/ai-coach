package com.aicoach.adjust;

import com.aicoach.adjust.AdjustmentDtos.AdjustmentOptions;
import com.aicoach.adjust.AdjustmentDtos.ExtendRequest;
import com.aicoach.adjust.AdjustmentDtos.MoreTimeRequest;
import com.aicoach.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Ways back on track when the next milestone no longer fits; each POST returns the options after the change. */
@RestController
@RequestMapping("/api/v1/goals/{goalId}/adjustments")
public class AdjustmentController {

    private final AdjustmentService adjustments;

    public AdjustmentController(AdjustmentService adjustments) {
        this.adjustments = adjustments;
    }

    @GetMapping
    public AdjustmentOptions options(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId) {
        return adjustments.options(user.id(), goalId);
    }

    @PostMapping("/extend")
    public AdjustmentOptions extend(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId,
                                    @Valid @RequestBody ExtendRequest req) {
        return adjustments.extend(user.id(), goalId, req.days());
    }

    @PostMapping("/more-time")
    public AdjustmentOptions moreTime(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId,
                                      @Valid @RequestBody MoreTimeRequest req) {
        return adjustments.moreTime(user.id(), goalId, req.minutesPerDay());
    }
}
