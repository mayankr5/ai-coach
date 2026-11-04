package com.aicoach.adjust;

import com.aicoach.ai.AiResult;

/** Suggests work to drop when a milestone no longer fits. Implementations must not touch the database. */
public interface ScopeCutter {

    /**
     * @return a suggestion that passes {@link AiScopeCutter#problems}, and the model that made it
     * @throws com.aicoach.plan.AiUnavailableException if no usable suggestion could be produced
     */
    AiResult<ScopeSuggestion> suggest(CutRequest request);
}
