package com.hammerly.ai.cache;

import com.hammerly.ai.config.AiStateProperties;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.redis.RedisStateClient;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RedisAiResponseCache implements AiResponseCache {
    private static final Logger log = LoggerFactory.getLogger(RedisAiResponseCache.class);

    private final RedisStateClient redis;
    private final AiStateProperties properties;
    private final AiMetrics metrics;

    public RedisAiResponseCache(RedisStateClient redis, AiStateProperties properties, AiMetrics metrics) {
        this.redis = redis;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public Optional<String> get(String key) {
        try {
            String value = redis.get(key);
            if (StringUtils.hasText(value)) {
                metrics.cacheHit();
                return Optional.of(value);
            }
            metrics.cacheMiss();
            return Optional.empty();
        } catch (RuntimeException exception) {
            metrics.cacheMiss();
            metrics.redisError("response_cache_read");
            log.warn("Redis AI response cache read failed; treating as a miss errorType={}",
                rootCauseName(exception));
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, String response) {
        try {
            redis.set(key, response, properties.responseCache().ttl());
        } catch (RuntimeException exception) {
            metrics.redisError("response_cache_write");
            log.warn("Redis AI response cache write failed; response will still be returned errorType={}",
                rootCauseName(exception));
        }
    }

    private String rootCauseName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }
}
