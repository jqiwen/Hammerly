package com.hammerly.ai.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiProviderAvailability {
    private final Environment environment;

    public AiProviderAvailability(Environment environment) {
        this.environment = environment;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(environment.getProperty("OPENAI_API_KEY"));
    }
}
