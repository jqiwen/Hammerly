package com.hammerly.ai.context;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hammerly.ai.context")
public record AiContextProperties(int recentTurns, int maxChars) {
}
