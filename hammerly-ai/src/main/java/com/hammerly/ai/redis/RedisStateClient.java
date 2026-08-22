package com.hammerly.ai.redis;

import java.time.Duration;
import java.util.List;

public interface RedisStateClient {
    List<String> listRange(String key);

    void appendAndTrim(String key, List<String> values, int maximumSize, Duration ttl);

    String get(String key);

    void set(String key, String value, Duration ttl);

    RedisCounter incrementWithExpiry(String key, Duration ttl);
}
