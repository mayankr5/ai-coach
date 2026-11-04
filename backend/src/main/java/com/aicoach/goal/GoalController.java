package com.aicoach.goal;

import com.aicoach.common.PageResponse;
import com.aicoach.goal.GoalDtos.*;
import com.aicoach.security.AuthUser;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateGoalRequest req) {
        return goalService.create(user.id(), req);
    }

    @GetMapping
    public PageResponse<GoalSummary> list(@AuthenticationPrincipal AuthUser user,
                                          @RequestParam(required = false) GoalStatus status,
                                          @ParameterObject
                                          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                          Pageable pageable) {
        return goalService.list(user.id(), status, pageable);
    }

    @GetMapping("/{id}")
    public GoalResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return goalService.get(user.id(), id);
    }

    @PatchMapping("/{id}")
    public GoalResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                               @Valid @RequestBody UpdateGoalRequest req) {
        return goalService.update(user.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        goalService.delete(user.id(), id);
    }
}
