package com.hammerly.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hammerly.ai.loadtest-provider")
public record LoadTestProviderProperties(
    Duration firstTokenDelay,
    Duration tokenInterval,
    int tokenCount,
    Duration timeoutDelay,
    double rateLimitRate,
    double serverErrorRate,
    double timeoutRate,
    double connectionFailureRate,
    double afterFirstTokenFailureRate
) {
}
