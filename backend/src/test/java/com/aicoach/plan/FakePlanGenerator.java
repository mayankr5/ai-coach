package com.aicoach.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Test double: returns scripted plans and records what it was asked. */
public class FakePlanGenerator implements PlanGenerator {

    private final List<Function<PlanRequest, GeneratedPlan>> script = new ArrayList<>();
    public final List<List<String>> receivedErrors = new ArrayList<>();

    public void willReturn(Function<PlanRequest, GeneratedPlan> response) {
        script.add(response);
    }

    public void reset() {
        script.clear();
        receivedErrors.clear();
    }

    @Override
    public GeneratedPlan generate(PlanRequest request, List<String> previousErrors) {
        receivedErrors.add(previousErrors);
        if (script.isEmpty()) {
            throw new AiUnavailableException("No scripted plan");
        }
        return script.removeFirst().apply(request);
    }
}
