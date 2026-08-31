package com.hammerly.ai.service;

import com.hammerly.ai.ratelimit.RateLimitDecision;
import com.hammerly.ai.rag.RagSource;
import java.util.List;
import reactor.core.publisher.Flux;

public record AiStreamResult(Flux<String> chunks, RateLimitDecision rateLimit, List<RagSource> sources) {
    public AiStreamResult {
        sources = List.copyOf(sources);
    }

    public AiStreamResult(Flux<String> chunks, RateLimitDecision rateLimit) {
        this(chunks, rateLimit, List.of());
    }
}
