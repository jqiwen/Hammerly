package com.hammerly.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hammerly.ai.exception.AiProviderUnavailableException;
import com.hammerly.ai.exception.AiCircuitOpenException;
import com.hammerly.ai.exception.AiConcurrencyLimitException;
import com.hammerly.ai.config.LlmResilienceProperties;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class OpenAiProviderExecutorTest {
    private SimpleMeterRegistry registry;
    private OpenAiProviderExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        executor = ProviderExecutorTestFactory.create(registry);
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
    void providerRetryAfterHeaderOverridesShorterConfiguredBackoff() {
        AtomicInteger attempts = new AtomicInteger();
        RateLimitException rateLimit = RateLimitException.builder()
            .headers(Headers.builder().put("Retry-After", "2").build())
            .error(error("rate_limit_exceeded", "requests"))
            .build();

        StepVerifier.withVirtualTime(() -> executor.stream("stream", () ->
                attempts.incrementAndGet() == 1
                    ? Flux.error(rateLimit)
                    : Flux.just("complete")))
            .expectSubscription()
            .expectNoEvent(Duration.ofMillis(1_999))
            .thenAwait(Duration.ofMillis(1))
            .expectNext("complete")
            .verifyComplete();

        assertThat(attempts).hasValue(2);
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
    void firstTokenTimeoutTerminatesAndReleasesResources() {
        LlmResilienceProperties settings = ProviderExecutorTestFactory.withMaxAttempts(
            ProviderExecutorTestFactory.properties(2, Duration.ZERO, 10, 5,
                Duration.ofSeconds(10), Duration.ofMillis(50)), 1);
        executor = ProviderExecutorTestFactory.create(registry, settings);

        StepVerifier.withVirtualTime(() -> executor.stream("stream", Flux::never))
            .thenAwait(Duration.ofMillis(50))
            .expectError(AiProviderUnavailableException.class)
            .verify();

        assertThat(registry.get("hammerly.ai.provider.timeout").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("hammerly.ai.provider.active").gauge().value()).isZero();
    }

    @Test
    void bulkheadRejectsExcessWithoutExceedingConfiguredConcurrency() throws Exception {
        LlmResilienceProperties settings = ProviderExecutorTestFactory.withMaxAttempts(
            ProviderExecutorTestFactory.properties(1, Duration.ZERO, 10, 5,
                Duration.ofSeconds(10), Duration.ofSeconds(5)), 1);
        executor = ProviderExecutorTestFactory.create(registry, settings);
        Sinks.One<Void> release = Sinks.one();
        CountDownLatch providerEntered = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();

        Disposable first = executor.stream("stream", () -> Flux.defer(() -> {
                providerEntered.countDown();
                int now = active.incrementAndGet();
                maximum.accumulateAndGet(now, Math::max);
                return release.asMono().thenMany(Flux.just("done"))
                    .doFinally(ignored -> active.decrementAndGet());
            }))
            .subscribeOn(Schedulers.parallel())
            .subscribe();
        assertThat(providerEntered.await(1, TimeUnit.SECONDS)).isTrue();

        StepVerifier.create(executor.stream("stream", () -> Flux.just("rejected")))
            .expectError(AiConcurrencyLimitException.class)
            .verify();

        release.tryEmitEmpty();
        first.dispose();
        assertThat(maximum).hasValue(1);
        assertThat(registry.get("hammerly.ai.bulkhead.rejected").counter().count()).isEqualTo(1.0);
    }

    @Test
    void circuitOpensFailsFastThenRecoversThroughHalfOpen() throws Exception {
        LlmResilienceProperties settings = ProviderExecutorTestFactory.withMaxAttempts(
            ProviderExecutorTestFactory.properties(4, Duration.ZERO, 2, 2,
                Duration.ofMillis(75), Duration.ofSeconds(5)), 1);
        executor = ProviderExecutorTestFactory.create(registry, settings);
        AtomicInteger providerCalls = new AtomicInteger();

        for (int index = 0; index < 2; index++) {
            StepVerifier.create(executor.stream("stream", () -> {
                    providerCalls.incrementAndGet();
                    return Flux.error(new SocketTimeoutException("timed out"));
                }))
                .expectError(AiProviderUnavailableException.class)
                .verify();
        }

        StepVerifier.create(executor.stream("stream", () -> {
                providerCalls.incrementAndGet();
                return Flux.just("should not run");
            }))
            .expectError(AiCircuitOpenException.class)
            .verify();
        assertThat(providerCalls).hasValue(2);

        Thread.sleep(125);
        StepVerifier.create(executor.stream("stream", () -> {
                providerCalls.incrementAndGet();
                return Flux.just("probe-1");
            }))
            .expectNext("probe-1")
            .verifyComplete();
        StepVerifier.create(executor.stream("stream", () -> {
                providerCalls.incrementAndGet();
                return Flux.just("probe-2");
            }))
            .expectNext("probe-2")
            .verifyComplete();
        StepVerifier.create(executor.stream("stream", () -> {
                providerCalls.incrementAndGet();
                return Flux.just("closed");
            }))
            .expectNext("closed")
            .verifyComplete();
        assertThat(providerCalls).hasValue(5);
        assertThat(registry.get("hammerly.ai.circuit_open.rejected").counter().count())
            .isEqualTo(1.0);
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
        assertThat(registry.get("llm.errors")
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
