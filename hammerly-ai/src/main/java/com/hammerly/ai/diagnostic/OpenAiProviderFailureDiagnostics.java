package com.hammerly.ai.diagnostic;

import com.hammerly.ai.config.OpenAiConfigurationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenAiProviderFailureDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(OpenAiProviderFailureDiagnostics.class);

    private final OpenAiProviderFailureClassifier classifier;
    private final OpenAiConfigurationState configurationState;

    public OpenAiProviderFailureDiagnostics(OpenAiProviderFailureClassifier classifier,
                                            OpenAiConfigurationState configurationState) {
        this.classifier = classifier;
        this.configurationState = configurationState;
    }

    public OpenAiProviderFailure classifyAndLog(Throwable failure, String operation,
                                                int attempt, long durationMs,
                                                boolean firstChunkEmitted) {
        OpenAiProviderFailure diagnostic = classifier.classify(failure);
        log.warn(
            "provider_failure category={} durationMs={} model={} status={} "
                + "exceptionClass={} code={} operation={} attempt={} firstChunkEmitted={}",
            diagnostic.category().tag(),
            durationMs,
            configurationState.snapshot().model(),
            diagnostic.status() == null ? "unavailable" : diagnostic.status(),
            diagnostic.exceptionClass(),
            diagnostic.code(),
            operation,
            attempt,
            firstChunkEmitted
        );
        return diagnostic;
    }

    public void logRetry(OpenAiProviderFailure failure, String operation,
                         int failedAttempt, long backoffMs) {
        log.info(
            "provider_retry category={} model={} status={} exceptionClass={} code={} "
                + "operation={} failedAttempt={} nextAttempt={} backoffMs={}",
            failure.category().tag(),
            configurationState.snapshot().model(),
            failure.status() == null ? "unavailable" : failure.status(),
            failure.exceptionClass(),
            failure.code(),
            operation,
            failedAttempt,
            failedAttempt + 1,
            backoffMs
        );
    }

    public void logSuccess(String operation, int attempts, long durationMs) {
        log.info(
            "provider_success operation={} durationMs={} model={} attempts={}",
            operation,
            durationMs,
            configurationState.snapshot().model(),
            attempts
        );
    }
}
