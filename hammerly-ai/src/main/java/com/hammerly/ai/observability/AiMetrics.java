package com.hammerly.ai.observability;

import com.hammerly.ai.diagnostic.OpenAiProviderFailure;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class AiMetrics {
    private final MeterRegistry registry;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void cacheHit() {
        registry.counter("hammerly.ai.cache.hit").increment();
    }

    public void cacheMiss() {
        registry.counter("hammerly.ai.cache.miss").increment();
    }

    public void rateLimitAllowed() {
        registry.counter("hammerly.ai.rate_limit.allowed").increment();
    }

    public void rateLimitRejected() {
        registry.counter("hammerly.ai.rate_limit.rejected").increment();
    }

    public void rateLimitRedisFailure() {
        registry.counter("hammerly.ai.rate_limit.redis_failure").increment();
        redisError("rate_limit");
    }

    public void conversationRead(boolean successful) {
        registry.counter("hammerly.ai.conversation.read", "outcome",
            successful ? "success" : "failure").increment();
    }

    public void conversationWrite(boolean successful) {
        registry.counter("hammerly.ai.conversation.write", "outcome",
            successful ? "success" : "failure").increment();
    }

    public void redisError(String component) {
        registry.counter("hammerly.ai.redis.error", "component", component).increment();
    }

    public void requestLatency(String outcome, long startedAtNanos) {
        registry.timer("hammerly.ai.request.latency", "outcome", outcome)
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }

    public void providerSuccess(String operation, long startedAtNanos) {
        registry.counter("hammerly.ai.provider.success", "operation", operation).increment();
        providerLatency(operation, "success", startedAtNanos);
    }

    public void providerFailure(String operation, OpenAiProviderFailure failure,
                                long startedAtNanos) {
        registry.counter("hammerly.ai.provider.failure",
            "operation", operation,
            "category", failure.category().tag()).increment();
        providerLatency(operation, "failure", startedAtNanos);
    }

    public void providerRetry(String operation, OpenAiProviderFailure failure) {
        registry.counter("hammerly.ai.provider.retry",
            "operation", operation,
            "category", failure.category().tag()).increment();
    }

    private void providerLatency(String operation, String outcome, long startedAtNanos) {
        registry.timer("hammerly.ai.provider.latency",
            "operation", operation,
            "outcome", outcome)
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }
}
