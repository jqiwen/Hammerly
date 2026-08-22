package com.hammerly.ai.ratelimit;

import com.hammerly.ai.config.AiStateProperties;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.redis.RedisCounter;
import com.hammerly.ai.redis.RedisStateClient;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RedisAiRateLimiter implements AiRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RedisAiRateLimiter.class);
    private static final String KEY_PREFIX = "hammerly:rate-limit:ai:";

    private final RedisStateClient redis;
    private final AiStateProperties properties;
    private final AiMetrics metrics;
    private final Clock clock;

    public RedisAiRateLimiter(RedisStateClient redis, AiStateProperties properties,
                              AiMetrics metrics, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public RateLimitDecision acquire(String userId) {
        int limit = properties.rateLimit().requests();
        long windowSeconds = Math.max(1, properties.rateLimit().window().toSeconds());
        long now = clock.instant().getEpochSecond();
        long window = Math.floorDiv(now, windowSeconds);
        long reset = Math.multiplyExact(window + 1, windowSeconds);
        String key = KEY_PREFIX + userId + ":" + window;

        try {
            Duration keyTtl = Duration.ofSeconds(windowSeconds + 1);
            RedisCounter counter = redis.incrementWithExpiry(key, keyTtl);
            boolean allowed = counter.count() <= limit;
            int remaining = (int) Math.max(0, limit - counter.count());
            if (allowed) {
                metrics.rateLimitAllowed();
            } else {
                metrics.rateLimitRejected();
            }
            return new RateLimitDecision(allowed, limit, remaining, reset, true);
        } catch (RuntimeException exception) {
            metrics.rateLimitRedisFailure();
            metrics.rateLimitAllowed();
            log.warn("Redis AI rate limit failed open for user={} errorType={}",
                userId, rootCauseName(exception));
            return new RateLimitDecision(true, limit, limit, reset, false);
        }
    }

    static String key(String userId, long window) {
        return KEY_PREFIX + userId + ":" + window;
    }

    private String rootCauseName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }
}
