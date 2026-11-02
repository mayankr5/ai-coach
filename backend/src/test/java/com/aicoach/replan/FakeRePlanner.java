package com.aicoach.replan;

import com.aicoach.plan.AiUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Test double: returns scripted changes and records every request. Throws when the script is empty. */
public class FakeRePlanner implements RePlanner {

    private final List<Function<RePlanRequest, PlanChanges>> script = new ArrayList<>();
    public final List<RePlanRequest> requests = new ArrayList<>();
    public final List<List<String>> receivedErrors = new ArrayList<>();

    public void willReturn(Function<RePlanRequest, PlanChanges> response) {
        script.add(response);
    }

    public void reset() {
        script.clear();
        requests.clear();
        receivedErrors.clear();
    }

    @Override
    public PlanChanges replan(RePlanRequest request, List<String> previousErrors) {
        requests.add(request);
        receivedErrors.add(previousErrors);
        if (script.isEmpty()) {
            throw new AiUnavailableException("No scripted re-plan");
        }
        return script.removeFirst().apply(request);
    }
}
