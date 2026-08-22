package com.hammerly.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class OpenAiStartupDiagnostics implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(OpenAiStartupDiagnostics.class);

    private final OpenAiConfigurationState configurationState;

    public OpenAiStartupDiagnostics(OpenAiConfigurationState configurationState) {
        this.configurationState = configurationState;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        OpenAiConfigurationSnapshot snapshot = configurationState.snapshot();
        log.info(
            "OpenAI configuration: apiKeyConfigured={} apiKeyLength={} model={}",
            snapshot.apiKeyConfigured(),
            snapshot.apiKeyLength(),
            snapshot.model()
        );
    }
}
