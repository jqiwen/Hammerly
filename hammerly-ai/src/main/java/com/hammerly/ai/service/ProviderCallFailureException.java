package com.hammerly.ai.service;

import com.hammerly.ai.diagnostic.OpenAiProviderFailure;
import com.hammerly.ai.diagnostic.AiProviderHttpException;
import com.openai.errors.OpenAIServiceException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;

public final class ProviderCallFailureException extends RuntimeException {
    private static final Set<Integer> RETRYABLE_SERVER_STATUSES = Set.of(500, 502, 503, 504);

    private final OpenAiProviderFailure failure;
    private final boolean firstChunkEmitted;

    public ProviderCallFailureException(Throwable cause, OpenAiProviderFailure failure,
                                        boolean firstChunkEmitted) {
        super(cause);
        this.failure = failure;
        this.firstChunkEmitted = firstChunkEmitted;
    }

    public OpenAiProviderFailure failure() {
        return failure;
    }

    public boolean retryable() {
        if (firstChunkEmitted) {
            return false;
        }
        return switch (failure.category()) {
            case TIMEOUT, CONNECTION_RESET, NETWORK, RATE_LIMIT -> true;
            case SERVER_ERROR -> failure.status() != null
                && RETRYABLE_SERVER_STATUSES.contains(failure.status());
            default -> false;
        };
    }

    public boolean countsForCircuitBreaker() {
        return switch (failure.category()) {
            case TIMEOUT, CONNECTION_RESET, NETWORK, RATE_LIMIT, SERVER_ERROR -> true;
            default -> false;
        };
    }

    public Optional<Duration> retryAfter() {
        Throwable current = getCause();
        while (current != null) {
            if (current instanceof AiProviderHttpException http
                    && http.retryAfter() != null) {
                return Optional.of(http.retryAfter());
            }
            if (current instanceof OpenAIServiceException serviceException) {
                Optional<Duration> delay = retryAfter(serviceException);
                if (delay.isPresent()) {
                    return delay;
                }
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    private Optional<Duration> retryAfter(OpenAIServiceException exception) {
        return exception.headers().names().stream()
            .filter(name -> name.equalsIgnoreCase("retry-after"))
            .flatMap(name -> exception.headers().values(name).stream())
            .map(String::strip)
            .map(this::parseRetryAfter)
            .flatMap(Optional::stream)
            .findFirst();
    }

    private Optional<Duration> parseRetryAfter(String value) {
        try {
            long seconds = Long.parseLong(value);
            return seconds >= 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant();
                Duration delay = Duration.between(Instant.now(), retryAt);
                return Optional.of(delay.isNegative() ? Duration.ZERO : delay);
            } catch (DateTimeParseException invalidHttpDate) {
                return Optional.empty();
            }
        }
    }
}
