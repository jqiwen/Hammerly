package com.hammerly.ai.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hammerly.ai.diagnostic.OpenAiProviderFailure;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AiMetricsTest {
    @Test
    void recordsCacheHitsMissesAndLlmErrorsWithBoundedLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiMetrics metrics = new AiMetrics(registry);

        metrics.cacheHit();
        metrics.cacheMiss();
        metrics.providerFailure("stream", new OpenAiProviderFailure(
            OpenAiProviderFailure.Category.TIMEOUT, null, null, "TimeoutException"),
            System.nanoTime());

        assertEquals(1.0, registry.get("redis.cache.hits").counter().count());
        assertEquals(1.0, registry.get("redis.cache.misses").counter().count());
        assertEquals(1.0, registry.get("llm.errors")
            .tag("operation", "stream")
            .tag("category", "timeout").counter().count());
    }

    @Test
    void activeConversationGaugeReturnsToZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiMetrics metrics = new AiMetrics(registry);

        metrics.aiRequestStarted();
        assertEquals(1.0, registry.get("active.conversations").gauge().value());

        metrics.requestCompleted("success", System.nanoTime());
        metrics.aiRequestFinished();

        assertEquals(1.0, registry.get("ai.requests").counter().count());
        assertEquals(1L, registry.get("ai.request.duration")
            .tag("outcome", "success").timer().count());
        assertEquals(0.0, registry.get("active.conversations").gauge().value());
    }
}
