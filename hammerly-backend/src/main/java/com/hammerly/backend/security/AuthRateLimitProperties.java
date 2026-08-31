package com.hammerly.backend.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hammerly.auth.rate-limit")
public record AuthRateLimitProperties(
    boolean enabled,
    boolean redisEnabled,
    boolean trustForwardedFor,
    int loginLimit,
    Duration loginWindow,
    int registerLimit,
    Duration registerWindow,
    int localMaxKeys
) {
    public AuthRateLimitProperties {
        requirePositive(loginLimit, "login-limit");
        requirePositive(registerLimit, "register-limit");
        requirePositive(localMaxKeys, "local-max-keys");
        requirePositive(loginWindow, "login-window");
        requirePositive(registerWindow, "register-window");
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
