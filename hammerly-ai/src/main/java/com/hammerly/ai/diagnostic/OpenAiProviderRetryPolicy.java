package com.hammerly.ai.diagnostic;

import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OpenAiProviderRetryPolicy {
    private static final int MAX_RETRIES = 2;
    private static final Duration FIRST_BACKOFF = Duration.ofMillis(300);
    private static final Duration SECOND_BACKOFF = Duration.ofMillis(800);
    private static final Set<Integer> RETRYABLE_SERVER_STATUSES = Set.of(500, 502, 503, 504);

    public boolean shouldRetry(OpenAiProviderFailure failure, int failedAttempt,
                               boolean firstChunkEmitted) {
        if (firstChunkEmitted || failedAttempt > MAX_RETRIES) {
            return false;
        }
        return switch (failure.category()) {
            case TIMEOUT, CONNECTION_RESET, NETWORK, RATE_LIMIT -> true;
            case SERVER_ERROR -> failure.status() != null
                && RETRYABLE_SERVER_STATUSES.contains(failure.status());
            default -> false;
        };
    }

    public Duration backoffAfter(int failedAttempt) {
        return failedAttempt <= 1 ? FIRST_BACKOFF : SECOND_BACKOFF;
    }
}
