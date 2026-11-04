package com.aicoach.plan;

import com.aicoach.ai.AiResult;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Test double: answers every request with {@link #answerWith}'s function and records what it was asked.
 * With no function set it fails like an unavailable AI. Not a script, because the nightly job may ask for
 * other tests' goals too.
 */
public class FakeWeekPlanner implements WeekPlanner {

    public static final String MODEL = "fake/week";

    private volatile Function<WeekRequest, PlannedWeek> responder;
    public final List<WeekRequest> requests = new CopyOnWriteArrayList<>();

    public void answerWith(Function<WeekRequest, PlannedWeek> responder) {
        this.responder = responder;
    }

    public void reset() {
        responder = null;
        requests.clear();
    }

    /** Requests made for one goal, found by its title. */
    public List<WeekRequest> requestsFor(String goalTitle) {
        return requests.stream().filter(r -> r.window().title().equals(goalTitle)).toList();
    }

    @Override
    public AiResult<PlannedWeek> plan(WeekRequest request) {
        requests.add(request);
        if (responder == null) {
            throw new AiUnavailableException("No scripted week");
        }
        return new AiResult<>(responder.apply(request), MODEL);
    }
}
