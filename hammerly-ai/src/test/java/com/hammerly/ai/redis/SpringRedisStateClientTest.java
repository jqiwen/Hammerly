package com.hammerly.ai.redis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class SpringRedisStateClientTest {
    @Test
    void redisFailureShortCircuitsFollowingOperations() {
        StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = org.mockito.Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("first")).thenThrow(new IllegalStateException("offline"));
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        SpringRedisStateClient client = new SpringRedisStateClient(redis, metrics);

        assertThatThrownBy(() -> client.get("first"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("offline");
        assertThatThrownBy(() -> client.get("second"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Redis is temporarily unavailable");

        verify(values, times(1)).get("first");
        verify(values, times(0)).get("second");
        org.assertj.core.api.Assertions.assertThat(metrics.get(
            "hammerly.ai.redis.short_circuit").counter().count()).isEqualTo(1);
    }
}
