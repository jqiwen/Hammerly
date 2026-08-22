package com.hammerly.ai.cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.support.AiTestFixtures;
import com.hammerly.ai.support.FakeRedisStateClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RedisAiResponseCacheTest {
    @Test
    void storesAndReadsCompletedResponse() {
        FakeRedisStateClient redis = new FakeRedisStateClient();
        RedisAiResponseCache cache = cache(redis);

        assertTrue(cache.get("cache-key").isEmpty());
        cache.put("cache-key", "cached answer");

        assertEquals("cached answer", cache.get("cache-key").orElseThrow());
    }

    @Test
    void redisFailureDegradesToCacheMiss() {
        FakeRedisStateClient redis = new FakeRedisStateClient();
        redis.failAllOperations();
        RedisAiResponseCache cache = cache(redis);

        assertTrue(cache.get("cache-key").isEmpty());
        assertDoesNotThrow(() -> cache.put("cache-key", "answer"));
    }

    private RedisAiResponseCache cache(FakeRedisStateClient redis) {
        return new RedisAiResponseCache(redis,
            AiTestFixtures.properties(20, 20, Duration.ofMinutes(1)),
            new AiMetrics(new SimpleMeterRegistry()));
    }
}
