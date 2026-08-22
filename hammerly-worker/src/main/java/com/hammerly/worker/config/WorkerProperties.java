package com.hammerly.worker.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "hammerly.worker")
public record WorkerProperties(
    @NotBlank String eventsTopic,
    @NotBlank String jobsTopic,
    @NotNull Duration processedEventTtl,
    @NotNull Duration processingLockTtl,
    @NotNull Duration summaryTtl
) {
}
