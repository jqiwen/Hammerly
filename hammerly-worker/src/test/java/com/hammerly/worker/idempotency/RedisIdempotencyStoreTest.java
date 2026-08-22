package com.hammerly.worker.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hammerly.worker.config.WorkerProperties;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisIdempotencyStoreTest {
    @Test
    void completedMarkerUsesConfiguredSevenDayTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.execute(any(RedisScript.class), any(java.util.List.class),
            any(), any())).thenReturn(1L);
        WorkerProperties properties = new WorkerProperties("events", "jobs",
            Duration.ofDays(7), Duration.ofMinutes(2), Duration.ofDays(7));
        RedisIdempotencyStore store = new RedisIdempotencyStore(redis, properties);
        UUID eventId = UUID.randomUUID();

        ProcessingClaim claim = store.claim(eventId);
        store.complete(claim);

        assertEquals(ProcessingClaim.Status.ACQUIRED, claim.status());
        verify(values).set("hammerly:worker:processed:" + eventId, "1", Duration.ofDays(7));
        verify(redis).execute(any(RedisScript.class),
            eq(java.util.List.of("hammerly:worker:processing:" + eventId)),
            eq(claim.lockToken()));
    }
}
