package com.hammerly.backend.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hammerly.backend.exception.AuthRateLimitExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class AuthRateLimiterTest {
    @Test
    void loginAndRegistrationHaveIndependentConfiguredThresholds() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties(
            true, false, false, 10, Duration.ofMinutes(1), 5, Duration.ofMinutes(10), 100);
        AuthRateLimiter limiter = new AuthRateLimiter(null, properties, new SimpleMeterRegistry(), Clock.systemUTC());

        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.check("login", "192.0.2.1", 10, Duration.ofMinutes(1));
        }
        assertThatThrownBy(() -> limiter.check("login", "192.0.2.1", 10, Duration.ofMinutes(1)))
            .isInstanceOf(AuthRateLimitExceededException.class);

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.check("register", "192.0.2.1", 5, Duration.ofMinutes(10));
        }
        assertThatThrownBy(() -> limiter.check("register", "192.0.2.1", 5, Duration.ofMinutes(10)))
            .isInstanceOf(AuthRateLimitExceededException.class);
    }

    @Test
    void disabledLimiterNeverRejects() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties(
            false, false, false, 1, Duration.ofMinutes(1), 1, Duration.ofMinutes(1), 10);
        AuthRateLimiter limiter = new AuthRateLimiter(null, properties, new SimpleMeterRegistry(), Clock.systemUTC());

        for (int attempt = 0; attempt < 100; attempt++) {
            limiter.check("login", "192.0.2.2", 1, Duration.ofMinutes(1));
        }
    }

    @Test
    void redisFailureFallsBackToLocalLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any())).thenThrow(new IllegalStateException("Redis unavailable"));
        AuthRateLimitProperties properties = new AuthRateLimitProperties(
            true, true, false, 1, Duration.ofMinutes(1), 1, Duration.ofMinutes(1), 10);
        AuthRateLimiter limiter = new AuthRateLimiter(redis, properties, new SimpleMeterRegistry(), Clock.systemUTC());

        limiter.check("login", "192.0.2.3", 1, Duration.ofMinutes(1));
        assertThatThrownBy(() -> limiter.check("login", "192.0.2.3", 1, Duration.ofMinutes(1)))
            .isInstanceOf(AuthRateLimitExceededException.class);
    }
}
