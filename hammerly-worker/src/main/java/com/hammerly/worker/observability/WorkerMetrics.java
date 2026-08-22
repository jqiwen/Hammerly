package com.hammerly.worker.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class WorkerMetrics {
    private final MeterRegistry registry;

    public WorkerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void processed(String eventType) {
        registry.counter("hammerly.worker.event.processed", "eventType", eventType).increment();
    }

    public void failed(String eventType) {
        registry.counter("hammerly.worker.event.failed", "eventType", eventType).increment();
    }

    public void retry(String eventType) {
        registry.counter("hammerly.worker.event.retry", "eventType", eventType).increment();
    }

    public void dlt(String eventType) {
        registry.counter("hammerly.worker.event.dlt", "eventType", eventType).increment();
    }

    public void duplicate(String eventType) {
        registry.counter("hammerly.worker.event.duplicate", "eventType", eventType).increment();
    }

    public double retryCount(String eventType) {
        return registry.counter("hammerly.worker.event.retry", "eventType", eventType).count();
    }

    public double dltCount(String eventType) {
        return registry.counter("hammerly.worker.event.dlt", "eventType", eventType).count();
    }

    public void completedAiTurn() {
        registry.counter("hammerly.worker.analytics.ai_turn.completed").increment();
    }

    public void summarySuccess(long startedAtNanos) {
        registry.counter("hammerly.worker.summary.success").increment();
        summaryLatency("success", startedAtNanos);
    }

    public void summaryFailure(long startedAtNanos) {
        registry.counter("hammerly.worker.summary.failure").increment();
        summaryLatency("failure", startedAtNanos);
    }

    private void summaryLatency(String result, long startedAtNanos) {
        registry.timer("hammerly.worker.summary.latency", "result", result)
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }
}
