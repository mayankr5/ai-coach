package com.aicoach.plan;

import com.aicoach.ai.AiResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Test double: returns scripted plans and records what it was asked. Throws when the script is empty. */
public class FakePlanGenerator implements PlanGenerator {

    public static final String MODEL = "fake/planner";

    private final List<Function<PlanRequest, GeneratedPlan>> script = new ArrayList<>();
    public final List<PlanRequest> requests = new ArrayList<>();

    public void willReturn(Function<PlanRequest, GeneratedPlan> response) {
        script.add(response);
    }

    public void reset() {
        script.clear();
        requests.clear();
    }

    @Override
    public AiResult<GeneratedPlan> generate(PlanRequest request) {
        requests.add(request);
        if (script.isEmpty()) {
            throw new AiUnavailableException("No scripted plan");
        }
        return new AiResult<>(script.removeFirst().apply(request), MODEL);
    }
}
