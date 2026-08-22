package com.hammerly.ai.config;

public record OpenAiConfigurationSnapshot(
    boolean apiKeyConfigured,
    int apiKeyLength,
    String model
) {
}
