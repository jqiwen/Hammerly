package com.hammerly.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "hammerly.ai.state")
public record AiStateProperties(
    @Valid @NotNull Conversation conversation,
    @Valid @NotNull ResponseCache responseCache,
    @Valid @NotNull RateLimit rateLimit
) {
    public record Conversation(
        @Min(1) int maxMessages,
        @NotNull Duration ttl
    ) {
    }

    public record ResponseCache(@NotNull Duration ttl) {
    }

    public record RateLimit(
        @Min(1) int requests,
        @NotNull Duration window
    ) {
    }
}
