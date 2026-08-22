package com.hammerly.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hammerly.ai")
public record AiPlatformProperties(
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    Duration streamTimeout,
    String internalToken
) {
}
