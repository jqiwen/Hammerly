package com.hammerly.ai.redis;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "hammerly.redis", name = "enabled", havingValue = "true",
    matchIfMissing = true)
public class SpringRedisStateClient implements RedisStateClient {
    private static final long FAILURE_BACKOFF_NANOS = Duration.ofSeconds(1).toNanos();
    private static final DefaultRedisScript<Long> APPEND_AND_TRIM = new DefaultRedisScript<>("""
        for i = 3, #ARGV do
          redis.call('RPUSH', KEYS[1], ARGV[i])
        end
        redis.call('LTRIM', KEYS[1], -tonumber(ARGV[1]), -1)
        redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
        return redis.call('LLEN', KEYS[1])
        """, Long.class);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>("""
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
          redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
        end
        local ttl = redis.call('TTL', KEYS[1])
        return {current, ttl}
        """, List.class);

    private final StringRedisTemplate redis;
    private final MeterRegistry metrics;
    private final AtomicLong unavailableUntilNanos = new AtomicLong();

    public SpringRedisStateClient(StringRedisTemplate redis) {
        this(redis, new SimpleMeterRegistry());
    }

    @Autowired
    public SpringRedisStateClient(StringRedisTemplate redis, MeterRegistry metrics) {
        this.redis = redis;
        this.metrics = metrics;
    }

    @Override
    public List<String> listRange(String key) {
        return timed("list_range", () -> {
            List<String> values = redis.opsForList().range(key, 0, -1);
            return values == null ? List.of() : List.copyOf(values);
        });
    }

    @Override
    public long appendAndTrim(String key, List<String> values, int maximumSize, Duration ttl) {
        if (values.isEmpty()) {
            return 0;
        }
        List<String> arguments = new ArrayList<>(values.size() + 2);
        arguments.add(Integer.toString(maximumSize));
        arguments.add(Long.toString(Math.max(1, ttl.toSeconds())));
        arguments.addAll(values);
        Long result = timed("append_trim",
            () -> redis.execute(APPEND_AND_TRIM, List.of(key), arguments.toArray()));
        if (result == null) {
            throw new IllegalStateException("Redis conversation script returned no result");
        }
        return result;
    }

    @Override
    public String get(String key) {
        return timed("get", () -> redis.opsForValue().get(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        timed("set", () -> redis.opsForValue().set(key, value, ttl));
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return timed("set_if_absent",
            () -> Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, value, ttl)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public RedisCounter incrementWithExpiry(String key, Duration ttl) {
        List<Long> result = timed("increment_expiry", () -> redis.execute(
            INCREMENT_WITH_EXPIRY, List.of(key), Long.toString(Math.max(1, ttl.toSeconds()))));
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Redis rate-limit script returned no result");
        }
        return new RedisCounter(result.get(0), result.get(1));
    }

    private <T> T timed(String operation, Supplier<T> action) {
        long started = System.nanoTime();
        if (started < unavailableUntilNanos.get()) {
            metrics.counter("hammerly.ai.redis.short_circuit", "operation", operation).increment();
            throw new IllegalStateException("Redis is temporarily unavailable");
        }
        try {
            T result = action.get();
            unavailableUntilNanos.set(0);
            return result;
        } catch (RuntimeException failure) {
            unavailableUntilNanos.set(System.nanoTime() + FAILURE_BACKOFF_NANOS);
            throw failure;
        } finally {
            metrics.timer("hammerly.ai.redis.operation.duration", "operation", operation)
                .record(Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private void timed(String operation, Runnable action) {
        timed(operation, () -> {
            action.run();
            return null;
        });
    }
}
