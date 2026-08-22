package com.hammerly.ai.config;

import org.springframework.stereotype.Component;

@Component
public class AiProviderAvailability {
    private final OpenAiConfigurationState configurationState;

    public AiProviderAvailability(OpenAiConfigurationState configurationState) {
        this.configurationState = configurationState;
    }

    public boolean isConfigured() {
        return configurationState.snapshot().apiKeyConfigured();
    }
}
