package com.hammerly.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hammerly.ai.config.OpenAiConfigurationState;
import com.hammerly.ai.diagnostic.OpenAiProviderFailureClassifier;
import com.hammerly.ai.diagnostic.OpenAiProviderFailureDiagnostics;
import com.hammerly.ai.diagnostic.OpenAiProviderRetryPolicy;
import com.hammerly.ai.exception.AiProviderUnavailableException;
import com.hammerly.ai.observability.AiMetrics;
import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.openai.models.ErrorObject;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class OpenAiProviderExecutorTest {
    private SimpleMeterRegistry registry;
    private OpenAiProviderExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        OpenAiProviderFailureClassifier classifier = new OpenAiProviderFailureClassifier();
        OpenAiConfigurationState configurationState = new OpenAiConfigurationState(
            new MockEnvironment()
                .withProperty("spring.ai.openai-sdk.chat.options.model", "gpt-5-mini")
        );
        executor = new OpenAiProviderExecutor(
            new OpenAiProviderFailureDiagnostics(classifier, configurationState),
            new OpenAiProviderRetryPolicy(),
            new AiMetrics(registry)
        );
    }

    @Test
    void timeoutBeforeFirstChunkRetriesThenSucceeds() {
        assertTransientRetry(
            new SocketTimeoutException("timed out"),
            "timeout"
        );
    }

    @Test
    void serviceUnavailableBeforeFirstChunkRetriesThenSucceeds() {
        assertTransientRetry(
            UnexpectedStatusCodeException.builder()
                .statusCode(503)
                .headers(emptyHeaders())
                .error(error("service_unavailable", "server_error"))
                .build(),
            "server_error"
        );
    }

    @Test
    void temporaryRateLimitBeforeFirstChunkRetriesThenSucceeds() {
        assertTransientRetry(
            RateLimitException.builder()
                .headers(emptyHeaders())
                .error(error("rate_limit_exceeded", "requests"))
                .build(),
            "rate_limit"
        );
    }

    @Test
    void authenticationFailureDoesNotRetry() {
        assertPermanentFailure(UnauthorizedException.builder()
            .headers(emptyHeaders())
            .error(error("invalid_api_key", "invalid_request_error"))
            .build());
    }

    @Test
    void insufficientQuotaDoesNotRetry() {
        assertPermanentFailure(RateLimitException.builder()
            .headers(emptyHeaders())
            .error(error("insufficient_quota", "insufficient_quota"))
            .build());
    }

    @Test
    void modelNotFoundDoesNotRetry() {
        assertPermanentFailure(BadRequestException.builder()
            .headers(emptyHeaders())
            .error(error("model_not_found", "invalid_request_error"))
            .build());
    }

    @Test
    void failureAfterFirstChunkDoesNotRestartStream() {
        AtomicInteger attempts = new AtomicInteger();

        Flux<String> stream = executor.stream("stream", () -> {
            attempts.incrementAndGet();
            return Flux.concat(
                Flux.just("partial"),
                Flux.error(new SocketTimeoutException("timed out"))
            );
        });

        StepVerifier.create(stream)
            .expectNext("partial")
            .expectError(AiProviderUnavailableException.class)
            .verify();

        assertThat(attempts).hasValue(1);
        assertThat(registry.find("hammerly.ai.provider.retry").counter()).isNull();
    }

    @Test
    void atMostTwoRetriesAreMade() {
        AtomicInteger attempts = new AtomicInteger();

        StepVerifier.withVirtualTime(() -> executor.stream("stream", () -> {
                if (attempts.incrementAndGet() < 3) {
                    return Flux.error(new SocketTimeoutException("timed out"));
                }
                return Flux.just("complete");
            }))
            .thenAwait(Duration.ofMillis(1_100))
            .expectNext("complete")
            .verifyComplete();

        assertThat(attempts).hasValue(3);
        assertThat(registry.get("hammerly.ai.provider.retry")
            .tag("category", "timeout").counter().count()).isEqualTo(2.0);
    }

    private void assertTransientRetry(Throwable firstFailure, String category) {
        AtomicInteger attempts = new AtomicInteger();

        StepVerifier.withVirtualTime(() -> executor.stream("stream", () ->
                attempts.incrementAndGet() == 1
                    ? Flux.error(firstFailure)
                    : Flux.just("complete")))
            .thenAwait(Duration.ofMillis(300))
            .expectNext("complete")
            .verifyComplete();

        assertThat(attempts).hasValue(2);
        assertThat(registry.get("hammerly.ai.provider.failure")
            .tag("category", category).counter().count()).isEqualTo(1.0);
        assertThat(registry.get("hammerly.ai.provider.retry")
            .tag("category", category).counter().count()).isEqualTo(1.0);
        assertThat(registry.get("hammerly.ai.provider.success").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("hammerly.ai.provider.latency")
            .tag("outcome", "failure").timer().count()).isEqualTo(1L);
        assertThat(registry.get("hammerly.ai.provider.latency")
            .tag("outcome", "success").timer().count()).isEqualTo(1L);
    }

    private void assertPermanentFailure(RuntimeException failure) {
        AtomicInteger attempts = new AtomicInteger();

        StepVerifier.create(executor.stream("stream", () -> {
                attempts.incrementAndGet();
                return Flux.error(failure);
            }))
            .expectError(AiProviderUnavailableException.class)
            .verify();

        assertThat(attempts).hasValue(1);
        assertThat(registry.find("hammerly.ai.provider.retry").counter()).isNull();
    }

    private Headers emptyHeaders() {
        return Headers.builder().build();
    }

    private ErrorObject error(String code, String type) {
        return ErrorObject.builder()
            .code(code)
            .message("test provider message")
            .param("test")
            .type(type)
            .build();
    }
}
