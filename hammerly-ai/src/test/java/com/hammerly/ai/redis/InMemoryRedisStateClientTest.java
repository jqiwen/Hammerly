package com.hammerly.ai.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InMemoryRedisStateClientTest {
    private final AtomicLong ticker = new AtomicLong();
    private final InMemoryRedisStateClient state = new InMemoryRedisStateClient(ticker::get);

    @Test
    void storesBoundedConversationHistoryAndExpiresIt() {
        assertEquals(2, state.appendAndTrim("conversation", List.of("one", "two"),
            3, Duration.ofSeconds(5)));
        assertEquals(3, state.appendAndTrim("conversation", List.of("three", "four"),
            3, Duration.ofSeconds(5)));
        assertEquals(List.of("two", "three", "four"), state.listRange("conversation"));

        ticker.addAndGet(Duration.ofSeconds(6).toNanos());
        assertEquals(List.of(), state.listRange("conversation"));
    }

    @Test
    void supportsCacheMarkersAndFixedWindowCounters() {
        state.set("cache", "answer", Duration.ofSeconds(5));
        assertEquals("answer", state.get("cache"));
        assertTrue(state.setIfAbsent("marker", "one", Duration.ofSeconds(5)));
        assertFalse(state.setIfAbsent("marker", "two", Duration.ofSeconds(5)));

        assertEquals(1, state.incrementWithExpiry("counter", Duration.ofSeconds(5)).count());
        ticker.addAndGet(Duration.ofSeconds(2).toNanos());
        RedisCounter second = state.incrementWithExpiry("counter", Duration.ofSeconds(5));
        assertEquals(2, second.count());
        assertEquals(3, second.ttlSeconds());

        ticker.addAndGet(Duration.ofSeconds(4).toNanos());
        assertNull(state.get("cache"));
        assertTrue(state.setIfAbsent("marker", "three", Duration.ofSeconds(5)));
        assertEquals(1, state.incrementWithExpiry("counter", Duration.ofSeconds(5)).count());
    }

    @Test
    void evictsLeastRecentlyUsedKeysAtTheSafetyLimit() {
        Duration ttl = Duration.ofHours(1);
        for (int index = 0; index < InMemoryRedisStateClient.MAXIMUM_KEYS; index++) {
            state.set("key-" + index, "value", ttl);
        }
        assertEquals("value", state.get("key-0"));

        state.set("overflow", "value", ttl);

        assertEquals("value", state.get("key-0"));
        assertNull(state.get("key-1"));
        assertEquals("value", state.get("overflow"));
    }
}
