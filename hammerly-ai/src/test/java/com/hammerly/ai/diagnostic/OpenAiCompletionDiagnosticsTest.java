package com.hammerly.ai.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenAiCompletionDiagnosticsTest {
    @Test
    void recognizesProviderLengthFinishReasons() {
        assertThat(OpenAiCompletionDiagnostics.isLengthLimited("length")).isTrue();
        assertThat(OpenAiCompletionDiagnostics.isLengthLimited("MAX_TOKENS")).isTrue();
        assertThat(OpenAiCompletionDiagnostics.isLengthLimited("stop")).isFalse();
        assertThat(OpenAiCompletionDiagnostics.isLengthLimited(null)).isFalse();
    }
}
