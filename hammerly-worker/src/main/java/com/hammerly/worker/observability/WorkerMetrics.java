package com.hammerly.worker.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WorkerMetrics {
    private static final Set<String> EVENT_TYPES = Set.of(
        "message.created",
        "conversation.summary.requested",
        "conversation.completed",
        "embedding.requested"
    );

    private final MeterRegistry registry;

    public WorkerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void processed(String eventType) {
        registry.counter("hammerly.worker.event.processed", "event_type", safeEventType(eventType))
            .increment();
    }

    public void failed(String eventType) {
        registry.counter("hammerly.worker.event.failed", "event_type", safeEventType(eventType))
            .increment();
    }

    public void retry(String eventType) {
        registry.counter("hammerly.worker.event.retry", "event_type", safeEventType(eventType))
            .increment();
    }

    public void dlt(String eventType) {
        registry.counter("hammerly.worker.event.dlt", "event_type", safeEventType(eventType))
            .increment();
    }

    public void duplicate(String eventType) {
        registry.counter("hammerly.worker.event.duplicate", "event_type", safeEventType(eventType))
            .increment();
    }

    public double retryCount(String eventType) {
        return registry.counter("hammerly.worker.event.retry", "event_type", safeEventType(eventType))
            .count();
    }

    public double dltCount(String eventType) {
        return registry.counter("hammerly.worker.event.dlt", "event_type", safeEventType(eventType))
            .count();
    }

    public void completedAiTurn() {
        registry.counter("hammerly.worker.analytics.ai_turn.completed").increment();
    }

    public void embeddingCompleted(int chunks, long startedAtNanos) {
        registry.counter("hammerly.worker.embedding.documents").increment();
        registry.summary("hammerly.worker.embedding.chunks").record(chunks);
        registry.timer("hammerly.worker.embedding.duration")
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }

    public void summarySuccess(long startedAtNanos) {
        registry.counter("hammerly.worker.summary.success").increment();
        summaryLatency("success", startedAtNanos);
    }

    public void summaryFailure(long startedAtNanos) {
        registry.counter("hammerly.worker.summary.failure").increment();
        summaryLatency("failure", startedAtNanos);
    }

    public void processingCompleted(String eventType, String outcome, long startedAtNanos) {
        Timer.builder("kafka.processing.duration")
            .description("Kafka processing duration from listener receipt through completion")
            .tag("event_type", safeEventType(eventType))
            .tag("outcome", outcome)
            .publishPercentileHistogram()
            .register(registry)
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }

    private void summaryLatency(String result, long startedAtNanos) {
        registry.timer("hammerly.worker.summary.latency", "result", result)
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }

    private String safeEventType(String eventType) {
        return EVENT_TYPES.contains(eventType) ? eventType : "unknown";
    }
}
