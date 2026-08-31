package com.hammerly.backend.security;

import com.hammerly.backend.exception.AuthRateLimitExceededException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class AuthRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
        return current
        """, Long.class);
    private static final String REDIS_PREFIX = "hammerly:auth:rate:v1:";
    private static final String OVERFLOW_KEY = "overflow";

    private final StringRedisTemplate redis;
    private final AuthRateLimitProperties properties;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final ConcurrentHashMap<String, LocalWindow> localWindows = new ConcurrentHashMap<>();
    private final AtomicInteger cleanupTicker = new AtomicInteger();

    @Autowired
    public AuthRateLimiter(ObjectProvider<StringRedisTemplate> redis,
                           AuthRateLimitProperties properties,
                           MeterRegistry metrics) {
        this(redis.getIfAvailable(), properties, metrics, Clock.systemUTC());
    }

    AuthRateLimiter(StringRedisTemplate redis, AuthRateLimitProperties properties,
                    MeterRegistry metrics, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public void checkLogin(HttpServletRequest request) {
        check("login", clientAddress(request), properties.loginLimit(), properties.loginWindow());
    }

    public void checkRegistration(HttpServletRequest request) {
        check("register", clientAddress(request), properties.registerLimit(), properties.registerWindow());
    }

    void check(String operation, String clientAddress, int limit, Duration window) {
        if (!properties.enabled()) return;
        String key = operation + ":" + hash(clientAddress);
        long count = redisCount(key, window);
        if (count < 0) count = localCount(key, window);
        metrics.counter("auth.rate_limit.attempts", "operation", operation,
            "outcome", count > limit ? "limited" : "allowed").increment();
        if (count > limit) {
            throw new AuthRateLimitExceededException(limit, window.toSeconds());
        }
    }

    private long redisCount(String key, Duration window) {
        if (!properties.redisEnabled() || redis == null) return -1;
        try {
            Long count = redis.execute(INCREMENT_SCRIPT, List.of(REDIS_PREFIX + key),
                Long.toString(window.toMillis()));
            return count == null ? -1 : count;
        } catch (RuntimeException exception) {
            metrics.counter("auth.rate_limit.redis_fallback").increment();
            log.warn("Auth rate-limit Redis failed; using local fallback errorType={}",
                exception.getClass().getSimpleName());
            return -1;
        }
    }

    private long localCount(String requestedKey, Duration window) {
        long now = clock.millis();
        if ((cleanupTicker.incrementAndGet() & 255) == 0) {
            localWindows.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis <= now);
        }
        String key = requestedKey;
        if (!localWindows.containsKey(key) && localWindows.size() >= properties.localMaxKeys()) {
            key = requestedKey.substring(0, requestedKey.indexOf(':') + 1) + OVERFLOW_KEY;
        }
        LocalWindow result = localWindows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAtMillis <= now) {
                return new LocalWindow(now + window.toMillis(), 1);
            }
            existing.count++;
            return existing;
        });
        return result.count;
    }

    private String clientAddress(HttpServletRequest request) {
        if (properties.trustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",", 2)[0].trim();
                if (!first.isBlank() && first.length() <= 128) return first;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class LocalWindow {
        private final long expiresAtMillis;
        private long count;

        private LocalWindow(long expiresAtMillis, long count) {
            this.expiresAtMillis = expiresAtMillis;
            this.count = count;
        }
    }
}
