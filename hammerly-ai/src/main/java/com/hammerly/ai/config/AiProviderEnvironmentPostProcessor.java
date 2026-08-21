package com.hammerly.ai.config;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Keeps process health available when the optional provider credential is absent.
 * Chat calls still fail explicitly; no fallback model is registered.
 */
public class AiProviderEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final String PROPERTY_SOURCE_NAME = "hammerlyMissingAiProvider";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean hasEnvironmentKey = StringUtils.hasText(environment.getProperty("OPENAI_API_KEY"));
        boolean hasExplicitSpringKey =
            StringUtils.hasText(environment.getProperty("spring.ai.openai-sdk.api-key"));

        if (!hasEnvironmentKey && !hasExplicitSpringKey) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                PROPERTY_SOURCE_NAME,
                Map.of("spring.ai.model.chat", "none")
            ));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
