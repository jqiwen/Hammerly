package com.hammerly.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AiProviderAvailabilityTest {
    @Test
    void usesTheEffectiveOpenAiSdkCredential() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty(OpenAiConfigurationState.API_KEY_PROPERTY, "configured-key");

        AiProviderAvailability availability = new AiProviderAvailability(
            new OpenAiConfigurationState(environment)
        );

        assertThat(availability.isConfigured()).isTrue();
    }

    @Test
    void isUnavailableWhenTheEffectiveCredentialIsBlank() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty(OpenAiConfigurationState.API_KEY_PROPERTY, "   ");

        AiProviderAvailability availability = new AiProviderAvailability(
            new OpenAiConfigurationState(environment)
        );

        assertThat(availability.isConfigured()).isFalse();
    }
}
