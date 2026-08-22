package com.hammerly.ai.service;

import com.hammerly.ai.diagnostic.OpenAiProviderFailure;
import com.hammerly.ai.diagnostic.OpenAiProviderFailureDiagnostics;
import com.hammerly.ai.diagnostic.OpenAiProviderRetryPolicy;
import com.hammerly.ai.exception.AiProviderUnavailableException;
import com.hammerly.ai.observability.AiMetrics;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class OpenAiProviderExecutor {
    private final OpenAiProviderFailureDiagnostics diagnostics;
    private final OpenAiProviderRetryPolicy retryPolicy;
    private final AiMetrics metrics;

    public OpenAiProviderExecutor(OpenAiProviderFailureDiagnostics diagnostics,
                                  OpenAiProviderRetryPolicy retryPolicy,
                                  AiMetrics metrics) {
        this.diagnostics = diagnostics;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
    }

    public <T> T execute(String operation, Supplier<T> providerCall) {
        long overallStartedAt = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            long attemptStartedAt = System.nanoTime();
            try {
                T result = providerCall.get();
                metrics.providerSuccess(operation, attemptStartedAt);
                diagnostics.logSuccess(operation, attempt, elapsedMillis(overallStartedAt));
                return result;
            } catch (RuntimeException failure) {
                OpenAiProviderFailure classified = diagnostics.classifyAndLog(
                    failure,
                    operation,
                    attempt,
                    elapsedMillis(attemptStartedAt),
                    false
                );
                metrics.providerFailure(operation, classified, attemptStartedAt);
                if (!retryPolicy.shouldRetry(classified, attempt, false)) {
                    throw unavailable(operation, failure);
                }

                Duration backoff = retryPolicy.backoffAfter(attempt);
                metrics.providerRetry(operation, classified);
                diagnostics.logRetry(classified, operation, attempt, backoff.toMillis());
                pause(backoff, failure);
            }
        }
    }

    public Flux<String> stream(String operation, Supplier<Flux<String>> providerCall) {
        return Flux.defer(() -> streamAttempt(
            operation,
            providerCall,
            1,
            System.nanoTime()
        ));
    }

    private Flux<String> streamAttempt(String operation, Supplier<Flux<String>> providerCall,
                                       int attempt, long overallStartedAt) {
        return Flux.defer(() -> {
            long attemptStartedAt = System.nanoTime();
            AtomicBoolean firstChunkEmitted = new AtomicBoolean(false);
            Flux<String> providerFlux;
            try {
                providerFlux = providerCall.get();
            } catch (RuntimeException failure) {
                providerFlux = Flux.error(failure);
            }

            return providerFlux
                .doOnNext(ignored -> firstChunkEmitted.set(true))
                .doOnComplete(() -> {
                    metrics.providerSuccess(operation, attemptStartedAt);
                    diagnostics.logSuccess(
                        operation,
                        attempt,
                        elapsedMillis(overallStartedAt)
                    );
                })
                .onErrorResume(failure -> {
                    boolean emitted = firstChunkEmitted.get();
                    OpenAiProviderFailure classified = diagnostics.classifyAndLog(
                        failure,
                        operation,
                        attempt,
                        elapsedMillis(attemptStartedAt),
                        emitted
                    );
                    metrics.providerFailure(operation, classified, attemptStartedAt);
                    if (!retryPolicy.shouldRetry(classified, attempt, emitted)) {
                        return Flux.error(unavailable(operation, failure));
                    }

                    Duration backoff = retryPolicy.backoffAfter(attempt);
                    metrics.providerRetry(operation, classified);
                    diagnostics.logRetry(classified, operation, attempt, backoff.toMillis());
                    return Mono.delay(backoff)
                        .thenMany(streamAttempt(
                            operation,
                            providerCall,
                            attempt + 1,
                            overallStartedAt
                        ));
                });
        });
    }

    private AiProviderUnavailableException unavailable(String operation, Throwable failure) {
        if (failure instanceof AiProviderUnavailableException unavailable) {
            return unavailable;
        }
        return new AiProviderUnavailableException(
            "AI provider " + operation + " failed.",
            failure
        );
    }

    private void pause(Duration backoff, RuntimeException originalFailure) {
        try {
            TimeUnit.MILLISECONDS.sleep(backoff.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiProviderUnavailableException(
                "AI provider retry was interrupted.",
                originalFailure
            );
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
