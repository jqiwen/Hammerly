package com.hammerly.ai.config;

import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAiConfigurationState {
    static final String API_KEY_PROPERTY = "spring.ai.openai-sdk.api-key";
    static final String MODEL_PROPERTY = "spring.ai.openai-sdk.chat.options.model";
    private static final Pattern SAFE_MODEL = Pattern.compile("[A-Za-z0-9._:-]{1,100}");
    private static final String UNKNOWN_MODEL = "unknown";

    private final Environment environment;

    public OpenAiConfigurationState(Environment environment) {
        this.environment = environment;
    }

    public OpenAiConfigurationSnapshot snapshot() {
        String apiKey = environment.getProperty(API_KEY_PROPERTY);
        String model = environment.getProperty(MODEL_PROPERTY);
        boolean configured = StringUtils.hasText(apiKey);
        return new OpenAiConfigurationSnapshot(
            configured,
            configured ? apiKey.length() : 0,
            safeModel(model)
        );
    }

    private String safeModel(String model) {
        if (!StringUtils.hasText(model) || !SAFE_MODEL.matcher(model).matches()) {
            return UNKNOWN_MODEL;
        }
        return model;
    }
}
