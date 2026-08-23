package com.hammerly.ai.observability;

import com.hammerly.ai.diagnostic.OpenAiProviderFailure;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class AiMetrics {
    private final MeterRegistry registry;
    private final Counter aiRequests;
    private final AtomicInteger activeConversations = new AtomicInteger();
    private final AtomicInteger activeProviderRequests = new AtomicInteger();
    private final AtomicInteger maxActiveConversations = new AtomicInteger();
    private final AtomicInteger maxActiveProviderRequests = new AtomicInteger();

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.aiRequests = Counter.builder("ai.requests")
            .description("AI requests accepted for processing")
            .register(registry);
        registry.gauge("active.conversations", activeConversations);
        registry.gauge("hammerly.ai.provider.active", activeProviderRequests);
        registry.gauge("hammerly.ai.requests.active.max", maxActiveConversations);
        registry.gauge("hammerly.ai.provider.active.max", maxActiveProviderRequests);
    }

    public void cacheHit() {
        registry.counter("redis.cache.hits").increment();
    }

    public void cacheMiss() {
        registry.counter("redis.cache.misses").increment();
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

    public void requestCompleted(String outcome, long startedAtNanos) {
        Timer.builder("ai.request.duration")
            .description("AI request duration from acceptance through terminal completion")
            .tag("outcome", outcome)
            .publishPercentileHistogram()
            .register(registry)
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }

    public void aiRequestStarted() {
        aiRequests.increment();
        int active = activeConversations.incrementAndGet();
        maxActiveConversations.accumulateAndGet(active, Math::max);
    }

    public void aiRequestFinished() {
        activeConversations.decrementAndGet();
    }

    public void providerRequestStarted(String operation) {
        int active = activeProviderRequests.incrementAndGet();
        maxActiveProviderRequests.accumulateAndGet(active, Math::max);
        registry.counter("hammerly.ai.provider.request", "operation", operation).increment();
    }

    public void providerRequestFinished() {
        activeProviderRequests.decrementAndGet();
    }

    public void providerFirstToken(String operation, long startedAtNanos) {
        registry.timer("hammerly.ai.provider.first_token.latency", "operation", operation)
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }

    public void providerSuccess(String operation, long startedAtNanos) {
        registry.counter("hammerly.ai.provider.success", "operation", operation).increment();
        providerLatency(operation, "success", startedAtNanos);
    }

    public void providerFailure(String operation, OpenAiProviderFailure failure,
                                long startedAtNanos) {
        registry.counter("llm.errors",
            "operation", operation,
            "category", failure.category().tag()).increment();
        providerLatency(operation, "failure", startedAtNanos);
        switch (failure.category()) {
            case RATE_LIMIT -> registry.counter("hammerly.ai.provider.http_429").increment();
            case SERVER_ERROR -> registry.counter("hammerly.ai.provider.http_5xx",
                "status", failure.status() == null ? "unknown" : failure.status().toString()).increment();
            case TIMEOUT -> registry.counter("hammerly.ai.provider.timeout").increment();
            default -> { }
        }
    }

    public void providerRetry(String operation, OpenAiProviderFailure failure) {
        registry.counter("hammerly.ai.provider.retry",
            "operation", operation,
            "category", failure.category().tag()).increment();
    }

    public void bulkheadRejected() {
        registry.counter("hammerly.ai.bulkhead.rejected").increment();
    }

    public void circuitOpenRejected() {
        registry.counter("hammerly.ai.circuit_open.rejected").increment();
    }

    public void circuitTransition(String transition) {
        registry.counter("hammerly.ai.circuit.transition", "transition", transition).increment();
    }

    public void kafkaPublishSuccess(String eventType) {
        registry.counter("hammerly.kafka.publish.success", "eventType", eventType).increment();
    }

    public void kafkaPublishFailure(String eventType) {
        registry.counter("hammerly.kafka.publish.failure", "eventType", eventType).increment();
    }

    private void providerLatency(String operation, String outcome, long startedAtNanos) {
        registry.timer("hammerly.ai.provider.latency",
            "operation", operation,
            "outcome", outcome)
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }

    /**
     * Prepared Phase 6 boundary for future RAG code. It is intentionally never
     * called by the current application, so no fake observations are emitted.
     */
    public void ragSearchCompleted(long startedAtNanos) {
        Timer.builder("rag.search.duration")
            .description("RAG search duration")
            .publishPercentileHistogram()
            .register(registry)
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }
}
