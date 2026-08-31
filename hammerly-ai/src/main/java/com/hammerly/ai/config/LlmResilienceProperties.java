package com.hammerly.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hammerly.ai.llm")
public record LlmResilienceProperties(
    Duration connectTimeout,
    Duration requestTimeout,
    Duration firstTokenTimeout,
    Duration idleTimeout,
    Retry retry,
    CircuitBreaker circuitBreaker,
    Bulkhead bulkhead
) {
    public record Retry(
        int maxAttempts,
        Duration initialBackoff,
        double multiplier,
        double jitter,
        Duration maxRetryAfter
    ) {
    }

    public record CircuitBreaker(
        int slidingWindowSize,
        int minimumCalls,
        float failureRateThreshold,
        float slowCallRateThreshold,
        Duration slowCallDuration,
        Duration openStateWait,
        int halfOpenPermittedCalls
    ) {
    }

    public record Bulkhead(
        int maxConcurrentCalls,
        Duration maxWait
    ) {
    }
}
