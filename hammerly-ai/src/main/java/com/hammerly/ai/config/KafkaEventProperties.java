package com.hammerly.ai.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "hammerly.kafka")
public record KafkaEventProperties(
    boolean enabled,
    @NotBlank String eventsTopic,
    @NotBlank String jobsTopic,
    @Min(2) int summaryAfterMessages,
    @NotNull Duration summaryRequestMarkerTtl
) {
}
