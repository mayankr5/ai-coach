package com.aicoach.replan;

import com.aicoach.ai.AiResult;
import com.aicoach.plan.AiUnavailableException;

import java.util.ArrayList;
import java.util.List;

/** Test double: returns scripted messages and records every call. Throws when the script is empty. */
public class FakeCoachWriter implements CoachWriter {

    public static final String MODEL = "fake/coach";

    private final List<String> script = new ArrayList<>();
    public final List<RePlanRequest> requests = new ArrayList<>();
    public final List<CatchUpPlan> plans = new ArrayList<>();

    public void willReturn(String message) {
        script.add(message);
    }

    public void reset() {
        script.clear();
        requests.clear();
        plans.clear();
    }

    @Override
    public AiResult<String> write(RePlanRequest request, CatchUpPlan plan) {
        requests.add(request);
        plans.add(plan);
        if (script.isEmpty()) {
            throw new AiUnavailableException("No scripted coach message");
        }
        return new AiResult<>(script.removeFirst(), MODEL);
    }
}
