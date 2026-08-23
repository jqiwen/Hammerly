package com.hammerly.ai.service;

import com.hammerly.ai.config.LlmResilienceProperties;
import com.hammerly.ai.config.OpenAiConfigurationState;
import com.hammerly.ai.diagnostic.OpenAiProviderFailureClassifier;
import com.hammerly.ai.diagnostic.OpenAiProviderFailureDiagnostics;
import com.hammerly.ai.observability.AiMetrics;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.springframework.mock.env.MockEnvironment;

final class ProviderExecutorTestFactory {
    private ProviderExecutorTestFactory() {
    }

    static OpenAiProviderExecutor create(MeterRegistry registry) {
        return create(registry, properties(20, Duration.ZERO, 10, 5,
            Duration.ofSeconds(10), Duration.ofSeconds(30)));
    }

    static OpenAiProviderExecutor create(MeterRegistry registry,
                                         LlmResilienceProperties properties) {
        OpenAiProviderFailureClassifier classifier = new OpenAiProviderFailureClassifier();
        OpenAiConfigurationState state = new OpenAiConfigurationState(new MockEnvironment()
            .withProperty("spring.ai.openai-sdk.chat.options.model", "gpt-5-mini"));
        CircuitBreakerConfig circuitConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(properties.circuitBreaker().slidingWindowSize())
            .minimumNumberOfCalls(properties.circuitBreaker().minimumCalls())
            .failureRateThreshold(properties.circuitBreaker().failureRateThreshold())
            .waitDurationInOpenState(properties.circuitBreaker().openStateWait())
            .permittedNumberOfCallsInHalfOpenState(
                properties.circuitBreaker().halfOpenPermittedCalls())
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordException(failure -> failure instanceof ProviderCallFailureException providerFailure
                && providerFailure.countsForCircuitBreaker())
            .build();
        IntervalFunction interval = IntervalFunction.ofExponentialRandomBackoff(
            properties.retry().initialBackoff(), properties.retry().multiplier(), 0.0);
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(properties.retry().maxAttempts())
            .intervalBiFunction((attempt, resultOrFailure) -> {
                long configuredBackoff = interval.apply(attempt);
                if (resultOrFailure.isLeft()
                        && resultOrFailure.getLeft() instanceof ProviderCallFailureException failure) {
                    return Math.max(configuredBackoff,
                        failure.retryAfter().map(Duration::toMillis).orElse(0L));
                }
                return configuredBackoff;
            })
            .retryOnException(failure -> failure instanceof ProviderCallFailureException providerFailure
                && providerFailure.retryable())
            .build();
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(properties.bulkhead().maxConcurrentCalls())
            .maxWaitDuration(properties.bulkhead().maxWait())
            .build();
        return new OpenAiProviderExecutor(
            new OpenAiProviderFailureDiagnostics(classifier, state),
            new AiMetrics(registry),
            properties,
            CircuitBreaker.of("test-llm", circuitConfig),
            Retry.of("test-llm", retryConfig),
            Bulkhead.of("test-llm", bulkheadConfig),
            TimeLimiter.of("test-llm", TimeLimiterConfig.custom()
                .timeoutDuration(properties.requestTimeout()).build()),
            Executors.newVirtualThreadPerTaskExecutor());
    }

    static LlmResilienceProperties properties(int maxConcurrent, Duration maxWait,
                                               int circuitWindow, int circuitMinimum,
                                               Duration circuitWait,
                                               Duration firstTokenTimeout) {
        return new LlmResilienceProperties(
            Duration.ofSeconds(2),
            Duration.ofSeconds(5),
            firstTokenTimeout,
            Duration.ofSeconds(30),
            new LlmResilienceProperties.Retry(3, Duration.ofMillis(300), 8.0 / 3.0, 0.0),
            new LlmResilienceProperties.CircuitBreaker(
                circuitWindow, circuitMinimum, 50.0f, 100.0f,
                Duration.ofSeconds(20), circuitWait, 2),
            new LlmResilienceProperties.Bulkhead(maxConcurrent, maxWait));
    }

    static LlmResilienceProperties withMaxAttempts(LlmResilienceProperties source,
                                                    int maxAttempts) {
        return new LlmResilienceProperties(source.connectTimeout(), source.requestTimeout(),
            source.firstTokenTimeout(), source.idleTimeout(),
            new LlmResilienceProperties.Retry(maxAttempts,
                source.retry().initialBackoff(), source.retry().multiplier(), 0.0),
            source.circuitBreaker(), source.bulkhead());
    }
}
