package com.hammerly.ai.support;

import com.hammerly.ai.redis.RedisCounter;
import com.hammerly.ai.redis.RedisStateClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakeRedisStateClient implements RedisStateClient {
    private final Map<String, List<String>> lists = new HashMap<>();
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Long> counters = new HashMap<>();
    private boolean fail;

    public void failAllOperations() {
        fail = true;
    }

    @Override
    public List<String> listRange(String key) {
        requireAvailable();
        return List.copyOf(lists.getOrDefault(key, List.of()));
    }

    @Override
    public long appendAndTrim(String key, List<String> newValues, int maximumSize, Duration ttl) {
        requireAvailable();
        List<String> current = new ArrayList<>(lists.getOrDefault(key, List.of()));
        current.addAll(newValues);
        int first = Math.max(0, current.size() - maximumSize);
        lists.put(key, new ArrayList<>(current.subList(first, current.size())));
        return lists.get(key).size();
    }

    @Override
    public String get(String key) {
        requireAvailable();
        return values.get(key);
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        requireAvailable();
        values.put(key, value);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        requireAvailable();
        return values.putIfAbsent(key, value) == null;
    }

    @Override
    public RedisCounter incrementWithExpiry(String key, Duration ttl) {
        requireAvailable();
        long count = counters.merge(key, 1L, Long::sum);
        return new RedisCounter(count, ttl.toSeconds());
    }

    private void requireAvailable() {
        if (fail) {
            throw new IllegalStateException("Redis unavailable");
        }
    }
}
