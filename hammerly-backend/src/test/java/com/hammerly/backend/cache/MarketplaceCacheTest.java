package com.hammerly.backend.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MarketplaceCacheTest {
    @Test
    void readsAndWritesAuctionWithBoundedTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("hammerly:marketplace:auction:7"))
            .thenReturn("{\"id\":7,\"title\":\"Camera\"}");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MarketplaceCache cache = cache(redis, registry);

        assertThat(cache.getAuction(7)).hasValueSatisfying(value ->
            assertThat(value).containsEntry("title", "Camera"));
        cache.putAuction(7, Map.of("id", 7, "title", "Camera"));

        verify(values).set(eq("hammerly:marketplace:auction:7"), argThat(json ->
            json.contains("\"id\":7") && json.contains("\"title\":\"Camera\"")),
            eq(Duration.ofMinutes(5)));
        assertThat(registry.counter("marketplace.cache.hits").count()).isEqualTo(1);
    }

    @Test
    void redisFailureBecomesMissAndInvalidationIsBestEffort() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("hammerly:marketplace:list:top:v1"))
            .thenThrow(new IllegalStateException("redis unavailable"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MarketplaceCache cache = cache(redis, registry);

        assertThat(cache.getTop()).isEmpty();
        cache.invalidateAuctionAfterCommit(9);

        verify(redis, never()).delete("hammerly:marketplace:auction:9");
        assertThat(registry.counter("marketplace.cache.misses").count()).isEqualTo(1);
        assertThat(registry.counter("marketplace.cache.errors", "operation", "read").count())
            .isEqualTo(1);
        assertThat(registry.counter("marketplace.cache.short_circuit").count()).isEqualTo(1);
    }

    @Test
    void slowRedisReadFallsBackWithinTheConfiguredDeadline() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("hammerly:marketplace:auction:7")).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return null;
        });
        MarketplaceCache cache = cache(redis, new SimpleMeterRegistry());

        long startedAt = System.nanoTime();
        assertThat(cache.getAuction(7)).isEmpty();
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(elapsedMillis).isLessThan(1_000);
    }

    @SuppressWarnings("unchecked")
    private MarketplaceCache cache(StringRedisTemplate redis, SimpleMeterRegistry registry) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        return new MarketplaceCache(provider, new ObjectMapper(),
            new MarketplaceCacheProperties(true, Duration.ofMinutes(5), Duration.ofSeconds(30),
                Duration.ofMillis(100)),
            registry);
    }
}
