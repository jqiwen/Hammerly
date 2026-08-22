package com.hammerly.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class OpenAiConfigurationStateTest {
    @Test
    void reportsOnlySafeCredentialMetadata() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty(OpenAiConfigurationState.API_KEY_PROPERTY, "test-secret-value")
            .withProperty(OpenAiConfigurationState.MODEL_PROPERTY, "gpt-5-mini");

        OpenAiConfigurationSnapshot snapshot = new OpenAiConfigurationState(environment).snapshot();

        assertThat(snapshot.apiKeyConfigured()).isTrue();
        assertThat(snapshot.apiKeyLength()).isEqualTo(17);
        assertThat(snapshot.model()).isEqualTo("gpt-5-mini");
        assertThat(snapshot.toString()).doesNotContain("test-secret-value");
    }

    @Test
    void reportsMissingCredentialWithoutGuessing() {
        OpenAiConfigurationSnapshot snapshot = new OpenAiConfigurationState(new MockEnvironment())
            .snapshot();

        assertThat(snapshot.apiKeyConfigured()).isFalse();
        assertThat(snapshot.apiKeyLength()).isZero();
        assertThat(snapshot.model()).isEqualTo("unknown");
    }

    @Test
    void rejectsUnsafeModelTextFromLogs() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty(OpenAiConfigurationState.MODEL_PROPERTY, "model\nforged-log=true");

        assertThat(new OpenAiConfigurationState(environment).snapshot().model()).isEqualTo("unknown");
    }
}
