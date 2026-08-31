package com.hammerly.ai.observability;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Request-local, low-cardinality latency data for the interactive SSE path. */
public final class AiRequestLatency {
    private static final Logger log = LoggerFactory.getLogger(AiRequestLatency.class);
    private static final long MAX_CLOCK_SKEW_MS = 60_000;

    private final long requestStartedAt = System.nanoTime();
    private final long coreToAiMs;
    private final AtomicLong providerStartedAt = new AtomicLong();
    private final AtomicLong providerTtftMs = new AtomicLong(-1);
    private final AtomicLong firstSseMs = new AtomicLong(-1);
    private final AtomicInteger providerAttempts = new AtomicInteger();
    private final AtomicBoolean logged = new AtomicBoolean();
    private volatile long contextMs;
    private volatile long ragMs;
    private volatile boolean cacheHit;

    private AiRequestLatency(Long coreAiStartedAtEpochMs) {
        this.coreToAiMs = boundedClockDelta(coreAiStartedAtEpochMs);
    }

    public static AiRequestLatency start(Long coreAiStartedAtEpochMs) {
        return new AiRequestLatency(coreAiStartedAtEpochMs);
    }

    public void contextBuilt(long contextMs, long ragMs) {
        this.contextMs = Math.max(0, contextMs);
        this.ragMs = Math.max(0, ragMs);
    }

    public void cacheHit() {
        this.cacheHit = true;
    }

    public void providerAttemptStarted() {
        providerStartedAt.compareAndSet(0, System.nanoTime());
        providerAttempts.incrementAndGet();
    }

    public void providerFirstToken() {
        long startedAt = providerStartedAt.get();
        if (startedAt > 0) {
            providerTtftMs.compareAndSet(-1, elapsedMillis(startedAt));
        }
    }

    public void firstSseToken() {
        firstSseMs.compareAndSet(-1, elapsedMillis(requestStartedAt));
    }

    public void completed(String outcome) {
        if (!logged.compareAndSet(false, true)) return;
        log.info("ai_latency outcome={} cacheHit={} coreToAiMs={} contextMs={} ragMs={} "
                + "providerTtftMs={} firstSseMs={} totalMs={} providerAttempts={}",
            outcome, cacheHit, coreToAiMs, contextMs, ragMs, providerTtftMs.get(),
            firstSseMs.get(), elapsedMillis(requestStartedAt), providerAttempts.get());
    }

    public int providerAttempts() {
        return providerAttempts.get();
    }

    public long providerTtftMs() {
        return providerTtftMs.get();
    }

    private long boundedClockDelta(Long startedAtEpochMs) {
        if (startedAtEpochMs == null || startedAtEpochMs <= 0) return -1;
        long elapsed = System.currentTimeMillis() - startedAtEpochMs;
        return elapsed >= 0 && elapsed <= MAX_CLOCK_SKEW_MS ? elapsed : -1;
    }

    private long elapsedMillis(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
    }
}
