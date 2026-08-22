package com.hammerly.ai.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.support.AiTestFixtures;
import com.hammerly.ai.support.FakeRedisStateClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RedisAiRateLimiterTest {
    @Test
    void allowsFirstTwentyAndRejectsTwentyFirstPerUser() {
        FakeRedisStateClient redis = new FakeRedisStateClient();
        RedisAiRateLimiter limiter = limiter(redis, Instant.parse("2026-08-22T12:00:10Z"));

        for (int request = 1; request <= 20; request++) {
            assertTrue(limiter.acquire("user-1").allowed());
        }
        assertFalse(limiter.acquire("user-1").allowed());
        assertTrue(limiter.acquire("user-2").allowed());
    }

    @Test
    void newWindowAllowsRequestsAgain() {
        FakeRedisStateClient redis = new FakeRedisStateClient();
        RedisAiRateLimiter firstWindow = limiter(redis, Instant.parse("2026-08-22T12:00:10Z"));
        for (int request = 1; request <= 21; request++) {
            firstWindow.acquire("user-1");
        }

        RedisAiRateLimiter nextWindow = limiter(redis, Instant.parse("2026-08-22T12:01:10Z"));

        assertTrue(nextWindow.acquire("user-1").allowed());
    }

    @Test
    void redisFailureFailsOpen() {
        FakeRedisStateClient redis = new FakeRedisStateClient();
        redis.failAllOperations();

        RateLimitDecision decision = limiter(redis, Instant.parse("2026-08-22T12:00:10Z"))
            .acquire("user-1");

        assertTrue(decision.allowed());
        assertFalse(decision.redisAvailable());
    }

    private RedisAiRateLimiter limiter(FakeRedisStateClient redis, Instant instant) {
        return new RedisAiRateLimiter(redis,
            AiTestFixtures.properties(20, 20, Duration.ofSeconds(60)),
            new AiMetrics(new SimpleMeterRegistry()),
            Clock.fixed(instant, ZoneOffset.UTC));
    }
}
