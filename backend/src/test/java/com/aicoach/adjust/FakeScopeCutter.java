package com.aicoach.adjust;

import com.aicoach.ai.AiResult;
import com.aicoach.plan.AiUnavailableException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Test double: answers with {@link #answerWith}'s function and records requests; fails like an unavailable AI otherwise. */
public class FakeScopeCutter implements ScopeCutter {

    public static final String MODEL = "fake/cutter";

    private volatile Function<CutRequest, ScopeSuggestion> responder;
    public final List<CutRequest> requests = new CopyOnWriteArrayList<>();

    public void answerWith(Function<CutRequest, ScopeSuggestion> responder) {
        this.responder = responder;
    }

    public void reset() {
        responder = null;
        requests.clear();
    }

    @Override
    public AiResult<ScopeSuggestion> suggest(CutRequest request) {
        requests.add(request);
        if (responder == null) {
            throw new AiUnavailableException("No scripted suggestion");
        }
        return new AiResult<>(responder.apply(request), MODEL);
    }
}
