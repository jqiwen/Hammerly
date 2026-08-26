package com.hammerly.ai.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Process-local fallback for demo-off mode. State is TTL-aware and bounded so a
 * long-running Cloud Run instance cannot accumulate keys without limit.
 */
@Component
@ConditionalOnProperty(prefix = "hammerly.redis", name = "enabled", havingValue = "false")
public class InMemoryRedisStateClient implements RedisStateClient {
    static final int MAXIMUM_KEYS = 10_000;

    private final Map<String, ExpiringValue> state =
        new LinkedHashMap<>(128, 0.75f, true);
    private final LongSupplier ticker;

    public InMemoryRedisStateClient() {
        this(System::nanoTime);
    }

    InMemoryRedisStateClient(LongSupplier ticker) {
        this.ticker = ticker;
    }

    @Override
    public synchronized List<String> listRange(String key) {
        ExpiringValue entry = liveEntry(key);
        if (entry == null) {
            return List.of();
        }
        if (!(entry.value() instanceof List<?> values)) {
            throw wrongType(key);
        }
        return values.stream().map(String.class::cast).toList();
    }

    @Override
    public synchronized long appendAndTrim(String key, List<String> values,
                                           int maximumSize, Duration ttl) {
        ExpiringValue entry = liveEntry(key);
        List<String> current = new ArrayList<>();
        if (entry != null) {
            if (!(entry.value() instanceof List<?> existing)) {
                throw wrongType(key);
            }
            existing.forEach(value -> current.add(String.class.cast(value)));
        }
        current.addAll(values);
        int first = Math.max(0, current.size() - maximumSize);
        List<String> trimmed = new ArrayList<>(current.subList(first, current.size()));
        put(key, trimmed, expiresAt(ttl));
        return trimmed.size();
    }

    @Override
    public synchronized String get(String key) {
        ExpiringValue entry = liveEntry(key);
        if (entry == null) {
            return null;
        }
        if (!(entry.value() instanceof String value)) {
            throw wrongType(key);
        }
        return value;
    }

    @Override
    public synchronized void set(String key, String value, Duration ttl) {
        put(key, value, expiresAt(ttl));
    }

    @Override
    public synchronized boolean setIfAbsent(String key, String value, Duration ttl) {
        if (liveEntry(key) != null) {
            return false;
        }
        put(key, value, expiresAt(ttl));
        return true;
    }

    @Override
    public synchronized RedisCounter incrementWithExpiry(String key, Duration ttl) {
        ExpiringValue entry = liveEntry(key);
        long count = 1;
        long expiration = expiresAt(ttl);
        if (entry != null) {
            if (!(entry.value() instanceof CounterValue counter)) {
                throw wrongType(key);
            }
            count = counter.count() + 1;
            expiration = entry.expiresAtNanos();
        }
        put(key, new CounterValue(count), expiration);
        long remainingNanos = Math.max(0, expiration - ticker.getAsLong());
        long remainingSeconds = (remainingNanos + 999_999_999L) / 1_000_000_000L;
        return new RedisCounter(count, remainingSeconds);
    }

    private ExpiringValue liveEntry(String key) {
        ExpiringValue entry = state.get(key);
        if (entry != null && entry.expiresAtNanos() <= ticker.getAsLong()) {
            state.remove(key);
            return null;
        }
        return entry;
    }

    private long expiresAt(Duration ttl) {
        long ttlNanos;
        try {
            ttlNanos = Math.max(1, ttl.toNanos());
        } catch (ArithmeticException exception) {
            ttlNanos = Long.MAX_VALUE;
        }
        long now = ticker.getAsLong();
        return ttlNanos == Long.MAX_VALUE || now > Long.MAX_VALUE - ttlNanos
            ? Long.MAX_VALUE
            : now + ttlNanos;
    }

    private void put(String key, Object value, long expiresAtNanos) {
        state.put(key, new ExpiringValue(value, expiresAtNanos));
        while (state.size() > MAXIMUM_KEYS) {
            String eldest = state.keySet().iterator().next();
            state.remove(eldest);
        }
    }

    private IllegalStateException wrongType(String key) {
        return new IllegalStateException("In-memory state key has an incompatible type: " + key);
    }

    private record ExpiringValue(Object value, long expiresAtNanos) {
    }

    private record CounterValue(long count) {
    }
}
