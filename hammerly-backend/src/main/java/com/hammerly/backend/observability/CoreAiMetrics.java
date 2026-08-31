package com.hammerly.backend.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CoreAiMetrics {
    private final MeterRegistry registry;

    public CoreAiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void completed(String operation, String outcome, long startedAtNanos) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAtNanos);
        Timer.builder("ai.core.proxy.duration")
            .description("Core to AI request/proxy duration")
            .tag("operation", operation)
            .tag("outcome", outcome)
            .publishPercentileHistogram()
            .register(registry).record(elapsed);
        if ("stream".equals(operation) || "chat".equals(operation)) {
            Timer.builder("ai.end.to.end.duration")
                .description("Core-observed AI request duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry).record(elapsed);
        }
    }
}
