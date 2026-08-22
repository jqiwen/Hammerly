package com.hammerly.ai.service;

import com.hammerly.ai.ratelimit.RateLimitDecision;
import reactor.core.publisher.Flux;

public record AiStreamResult(Flux<String> chunks, RateLimitDecision rateLimit) {
}
