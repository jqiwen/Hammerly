package com.hammerly.ai.service;

import com.hammerly.ai.observability.AiRequestLatency;
import com.hammerly.ai.ratelimit.RateLimitDecision;
import com.hammerly.ai.rag.RagSource;
import java.util.List;
import reactor.core.publisher.Flux;

public record AiStreamResult(Flux<String> chunks, RateLimitDecision rateLimit,
                             List<RagSource> sources, AiRequestLatency latency) {
    public AiStreamResult {
        sources = List.copyOf(sources);
    }

    public AiStreamResult(Flux<String> chunks, RateLimitDecision rateLimit) {
        this(chunks, rateLimit, List.of(), null);
    }

    public AiStreamResult(Flux<String> chunks, RateLimitDecision rateLimit,
                          List<RagSource> sources) {
        this(chunks, rateLimit, sources, null);
    }

    public void firstSseToken() {
        if (latency != null) latency.firstSseToken();
    }

    public void completeLatency(String outcome) {
        if (latency != null) latency.completed(outcome);
    }
}
