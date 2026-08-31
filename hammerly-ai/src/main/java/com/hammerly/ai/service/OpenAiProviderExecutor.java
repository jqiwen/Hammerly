package com.hammerly.ai.service;

import com.hammerly.ai.config.LlmResilienceProperties;
import com.hammerly.ai.diagnostic.OpenAiProviderFailure;
import com.hammerly.ai.diagnostic.OpenAiProviderFailureDiagnostics;
import com.hammerly.ai.exception.AiCircuitOpenException;
import com.hammerly.ai.exception.AiConcurrencyLimitException;
import com.hammerly.ai.exception.AiProviderUnavailableException;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.observability.AiRequestLatency;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class OpenAiProviderExecutor {
    private static final Logger log = LoggerFactory.getLogger(OpenAiProviderExecutor.class);

    private final OpenAiProviderFailureDiagnostics diagnostics;
    private final AiMetrics metrics;
    private final LlmResilienceProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final TimeLimiter timeLimiter;
    private final ExecutorService blockingExecutor;
    private final AtomicInteger bulkheadRejectionLogSequence = new AtomicInteger();
    private final AtomicInteger circuitRejectionLogSequence = new AtomicInteger();

    public OpenAiProviderExecutor(OpenAiProviderFailureDiagnostics diagnostics,
                                  AiMetrics metrics,
                                  LlmResilienceProperties properties,
                                  CircuitBreaker circuitBreaker,
                                  Retry retry,
                                  Bulkhead bulkhead,
                                  TimeLimiter timeLimiter,
                                  @Qualifier("llmBlockingExecutor") ExecutorService blockingExecutor) {
        this.diagnostics = diagnostics;
        this.metrics = metrics;
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.bulkhead = bulkhead;
        this.timeLimiter = timeLimiter;
        this.blockingExecutor = blockingExecutor;
        registerEventLogging();
    }

    public <T> T execute(String operation, Supplier<T> providerCall) {
        long overallStartedAt = System.nanoTime();
        AtomicInteger attempts = new AtomicInteger();
        Supplier<T> guarded = Bulkhead.decorateSupplier(bulkhead,
            Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> executeAttempt(
                    operation, providerCall, attempts.incrementAndGet(), overallStartedAt))));
        try {
            return guarded.get();
        } catch (RuntimeException failure) {
            throw terminalException(operation, failure);
        }
    }

    public Flux<String> stream(String operation, Supplier<Flux<String>> providerCall) {
        return Flux.deferContextual(context -> {
            long overallStartedAt = System.nanoTime();
            AtomicInteger attempts = new AtomicInteger();
            AiRequestLatency latency = context.getOrDefault(AiRequestLatency.class, null);
            return Flux.defer(() -> streamAttempt(operation, providerCall,
                    attempts.incrementAndGet(), overallStartedAt, latency))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(BulkheadOperator.of(bulkhead))
                .onErrorMap(failure -> terminalException(operation, failure));
        });
    }

    private <T> T executeAttempt(String operation, Supplier<T> providerCall, int attempt,
                                 long overallStartedAt) {
        long attemptStartedAt = System.nanoTime();
        metrics.providerRequestStarted(operation);
        try {
            T result = timeLimiter.executeFutureSupplier(
                () -> blockingExecutor.submit(providerCall::get));
            metrics.providerSuccess(operation, attemptStartedAt);
            diagnostics.logSuccess(operation, attempt, elapsedMillis(overallStartedAt));
            return result;
        } catch (Exception failure) {
            throw classifiedFailure(operation, failure, attempt, attemptStartedAt, false);
        } finally {
            metrics.providerRequestFinished();
        }
    }

    private Flux<String> streamAttempt(String operation, Supplier<Flux<String>> providerCall,
                                       int attempt, long overallStartedAt,
                                       AiRequestLatency latency) {
        return Flux.defer(() -> {
            long attemptStartedAt = System.nanoTime();
            AtomicBoolean firstChunkEmitted = new AtomicBoolean();
            if (latency != null) latency.providerAttemptStarted();
            metrics.providerRequestStarted(operation);

            Flux<String> providerFlux;
            try {
                providerFlux = providerCall.get();
            } catch (RuntimeException failure) {
                providerFlux = Flux.error(failure);
            }

            return providerFlux
                .timeout(Mono.delay(properties.firstTokenTimeout()),
                    ignored -> Mono.delay(properties.idleTimeout()))
                .doOnNext(ignored -> {
                    if (firstChunkEmitted.compareAndSet(false, true)) {
                        metrics.providerFirstToken(operation, attemptStartedAt);
                        if (latency != null) latency.providerFirstToken();
                    }
                })
                .doOnComplete(() -> {
                    metrics.providerSuccess(operation, attemptStartedAt);
                    diagnostics.logSuccess(operation, attempt, elapsedMillis(overallStartedAt));
                })
                .onErrorMap(failure -> classifiedFailure(operation, failure, attempt,
                    attemptStartedAt, firstChunkEmitted.get()))
                .doFinally(ignored -> metrics.providerRequestFinished());
        });
    }

    private ProviderCallFailureException classifiedFailure(String operation, Throwable failure,
                                                            int attempt, long attemptStartedAt,
                                                            boolean firstChunkEmitted) {
        OpenAiProviderFailure classified = diagnostics.classifyAndLog(
            failure, operation, attempt, elapsedMillis(attemptStartedAt), firstChunkEmitted);
        metrics.providerFailure(operation, classified, attemptStartedAt);
        return new ProviderCallFailureException(failure, classified, firstChunkEmitted);
    }

    private RuntimeException terminalException(String operation, Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof BulkheadFullException full) {
                return new AiConcurrencyLimitException(full);
            }
            if (current instanceof CallNotPermittedException open) {
                return new AiCircuitOpenException(open);
            }
            if (current instanceof ProviderCallFailureException providerFailure) {
                Throwable cause = providerFailure.getCause();
                if (cause instanceof AiProviderUnavailableException unavailable) {
                    return unavailable;
                }
                return new AiProviderUnavailableException(
                    "AI provider " + operation + " failed.", cause);
            }
            current = current.getCause();
        }
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        return new AiProviderUnavailableException("AI provider " + operation + " failed.", failure);
    }

    private void registerEventLogging() {
        retry.getEventPublisher().onRetry(event -> {
            Throwable failure = event.getLastThrowable();
            if (failure instanceof ProviderCallFailureException providerFailure) {
                metrics.providerRetry("provider", providerFailure.failure());
                diagnostics.logRetry(providerFailure.failure(), "provider",
                    event.getNumberOfRetryAttempts(), event.getWaitInterval().toMillis());
            }
        });
        bulkhead.getEventPublisher().onCallRejected(event -> {
            metrics.bulkheadRejected();
            int sequence = bulkheadRejectionLogSequence.incrementAndGet();
            if (sequence == 1 || sequence % 100 == 0) {
                log.warn("bulkhead_rejected name={} count={} maxConcurrentCalls={}",
                    bulkhead.getName(), sequence, properties.bulkhead().maxConcurrentCalls());
            }
        });
        circuitBreaker.getEventPublisher()
            .onCallNotPermitted(event -> {
                metrics.circuitOpenRejected();
                int sequence = circuitRejectionLogSequence.incrementAndGet();
                if (sequence == 1 || sequence % 100 == 0) {
                    log.warn("circuit_open_rejected name={} count={}",
                        circuitBreaker.getName(), sequence);
                }
            })
            .onStateTransition(event -> {
                String transition = event.getStateTransition().toString().toLowerCase();
                metrics.circuitTransition(transition);
                log.warn("circuit_state_transition name={} transition={}",
                    circuitBreaker.getName(), transition);
            });
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
