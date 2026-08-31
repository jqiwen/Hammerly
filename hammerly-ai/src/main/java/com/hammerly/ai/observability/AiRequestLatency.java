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
    private volatile long redisSummaryMs;
    private volatile long ragMs;
    private volatile long kbVersionMs;
    private volatile long ragCacheMs;
    private volatile long embeddingMs;
    private volatile long vectorSearchMs;
    private volatile long faqCacheMs;
    private volatile boolean cacheHit;
    private volatile boolean fastFaqCacheHit;

    private AiRequestLatency(Long coreAiStartedAtEpochMs) {
        this.coreToAiMs = boundedClockDelta(coreAiStartedAtEpochMs);
    }

    public static AiRequestLatency start(Long coreAiStartedAtEpochMs) {
        return new AiRequestLatency(coreAiStartedAtEpochMs);
    }

    public void contextBuilt(long contextMs, long redisSummaryMs, long ragMs,
                             long kbVersionMs, long ragCacheMs, long embeddingMs,
                             long vectorSearchMs) {
        this.contextMs = Math.max(0, contextMs);
        this.redisSummaryMs = Math.max(0, redisSummaryMs);
        this.ragMs = Math.max(0, ragMs);
        this.kbVersionMs = Math.max(0, kbVersionMs);
        this.ragCacheMs = Math.max(0, ragCacheMs);
        this.embeddingMs = Math.max(0, embeddingMs);
        this.vectorSearchMs = Math.max(0, vectorSearchMs);
    }

    public void faqCacheLookup(long durationMs, boolean hit) {
        this.faqCacheMs = Math.max(0, durationMs);
        this.fastFaqCacheHit = hit;
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
        log.info("ai_latency outcome={} cacheHit={} fastFaqCacheHit={} coreToAiMs={} "
                + "contextMs={} redisSummaryMs={} ragMs={} kbVersionMs={} ragCacheMs={} "
                + "embeddingMs={} vectorSearchMs={} faqCacheMs={} providerTtftMs={} "
                + "firstSseMs={} totalMs={} providerAttempts={}",
            outcome, cacheHit, fastFaqCacheHit, coreToAiMs, contextMs, redisSummaryMs,
            ragMs, kbVersionMs, ragCacheMs, embeddingMs, vectorSearchMs, faqCacheMs,
            providerTtftMs.get(), firstSseMs.get(), elapsedMillis(requestStartedAt),
            providerAttempts.get());
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
