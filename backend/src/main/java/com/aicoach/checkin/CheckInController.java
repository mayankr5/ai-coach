package com.aicoach.checkin;

import com.aicoach.checkin.CheckInDtos.*;
import com.aicoach.common.PageResponse;
import com.aicoach.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/goals/{goalId}")
public class CheckInController {

    private final CheckInService checkInService;
    private final ProgressService progressService;

    public CheckInController(CheckInService checkInService, ProgressService progressService) {
        this.checkInService = checkInService;
        this.progressService = progressService;
    }

    @PostMapping("/check-ins")
    public CheckInResponse submit(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId,
                                  @Valid @RequestBody CheckInRequest req) {
        return checkInService.submit(user.id(), goalId, req);
    }

    @GetMapping("/check-ins")
    public PageResponse<CheckInSummary> history(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId,
                                                @PageableDefault(size = 30, sort = "checkInDate",
                                                        direction = Sort.Direction.DESC) Pageable pageable) {
        return checkInService.history(user.id(), goalId, pageable);
    }

    @GetMapping("/progress")
    public ProgressResponse progress(@AuthenticationPrincipal AuthUser user, @PathVariable Long goalId) {
        return progressService.progress(user.id(), goalId);
    }
}
