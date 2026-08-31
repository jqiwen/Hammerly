package com.hammerly.ai.config;

import com.hammerly.ai.service.ProviderCallFailureException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({LlmResilienceProperties.class, LoadTestProviderProperties.class})
public class LlmResilienceConfiguration {
    public static final String INSTANCE_NAME = "hammerly-llm";

    @Bean
    CircuitBreaker llmCircuitBreaker(CircuitBreakerRegistry registry,
                                     LlmResilienceProperties properties) {
        LlmResilienceProperties.CircuitBreaker settings = properties.circuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowSize(settings.slidingWindowSize())
            .minimumNumberOfCalls(settings.minimumCalls())
            .failureRateThreshold(settings.failureRateThreshold())
            .slowCallRateThreshold(settings.slowCallRateThreshold())
            .slowCallDurationThreshold(settings.slowCallDuration())
            .waitDurationInOpenState(settings.openStateWait())
            .permittedNumberOfCallsInHalfOpenState(settings.halfOpenPermittedCalls())
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordException(failure -> failure instanceof ProviderCallFailureException providerFailure
                && providerFailure.countsForCircuitBreaker())
            .build();
        return registry.circuitBreaker(INSTANCE_NAME, config);
    }

    @Bean
    Retry llmRetry(RetryRegistry registry, LlmResilienceProperties properties) {
        LlmResilienceProperties.Retry settings = properties.retry();
        IntervalFunction interval = IntervalFunction.ofExponentialRandomBackoff(
            settings.initialBackoff(), settings.multiplier(), settings.jitter());
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(settings.maxAttempts())
            .intervalBiFunction((attempt, resultOrFailure) -> {
                long configuredBackoff = interval.apply(attempt);
                if (resultOrFailure.isLeft()
                        && resultOrFailure.getLeft() instanceof ProviderCallFailureException failure) {
                    long providerDelay = failure.retryAfter()
                        .map(delay -> delay.compareTo(settings.maxRetryAfter()) > 0
                            ? settings.maxRetryAfter() : delay)
                        .map(Duration::toMillis).orElse(0L);
                    return Math.max(configuredBackoff, providerDelay);
                }
                return configuredBackoff;
            })
            .retryOnException(failure -> failure instanceof ProviderCallFailureException providerFailure
                && providerFailure.retryable())
            .build();
        return registry.retry(INSTANCE_NAME, config);
    }

    @Bean
    Bulkhead llmBulkhead(BulkheadRegistry registry, LlmResilienceProperties properties) {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(properties.bulkhead().maxConcurrentCalls())
            .maxWaitDuration(properties.bulkhead().maxWait())
            .build();
        return registry.bulkhead(INSTANCE_NAME, config);
    }

    @Bean
    TimeLimiter llmTimeLimiter(TimeLimiterRegistry registry,
                               LlmResilienceProperties properties) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(properties.requestTimeout())
            .cancelRunningFuture(true)
            .build();
        return registry.timeLimiter(INSTANCE_NAME, config);
    }

    @Bean(destroyMethod = "close")
    ExecutorService llmBlockingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
