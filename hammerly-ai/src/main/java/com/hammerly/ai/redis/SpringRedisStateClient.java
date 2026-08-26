package com.hammerly.ai.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "hammerly.redis", name = "enabled", havingValue = "true",
    matchIfMissing = true)
public class SpringRedisStateClient implements RedisStateClient {
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

    public SpringRedisStateClient(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public List<String> listRange(String key) {
        List<String> values = redis.opsForList().range(key, 0, -1);
        return values == null ? List.of() : List.copyOf(values);
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
        Long result = redis.execute(APPEND_AND_TRIM, List.of(key), arguments.toArray());
        if (result == null) {
            throw new IllegalStateException("Redis conversation script returned no result");
        }
        return result;
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, value, ttl));
    }

    @Override
    @SuppressWarnings("unchecked")
    public RedisCounter incrementWithExpiry(String key, Duration ttl) {
        List<Long> result = redis.execute(INCREMENT_WITH_EXPIRY, List.of(key),
            Long.toString(Math.max(1, ttl.toSeconds())));
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Redis rate-limit script returned no result");
        }
        return new RedisCounter(result.get(0), result.get(1));
    }
}
