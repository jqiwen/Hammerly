package com.hammerly.ai.service;

import com.hammerly.ai.ratelimit.RateLimitDecision;
import com.hammerly.ai.rag.RagSource;
import java.util.List;

public record AiChatResult(String answer, RateLimitDecision rateLimit, List<RagSource> sources) {
    public AiChatResult {
        sources = List.copyOf(sources);
    }

    public AiChatResult(String answer, RateLimitDecision rateLimit) {
        this(answer, rateLimit, List.of());
    }
}
