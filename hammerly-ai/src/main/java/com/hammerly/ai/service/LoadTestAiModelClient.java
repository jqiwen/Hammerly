package com.hammerly.ai.service;

import com.hammerly.ai.config.LoadTestProviderProperties;
import com.hammerly.ai.diagnostic.AiProviderHttpException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Deterministic streaming provider used only with the explicit loadtest profile. */
@Component
@Profile("loadtest")
public class LoadTestAiModelClient implements AiModelClient {
    private static final long HASH_MULTIPLIER = 2_654_435_761L;

    private final LoadTestProviderProperties properties;
    private final OpenAiProviderExecutor providerExecutor;
    private final AtomicLong invocationSequence = new AtomicLong();

    public LoadTestAiModelClient(LoadTestProviderProperties properties,
                                 OpenAiProviderExecutor providerExecutor) {
        this.properties = properties;
        this.providerExecutor = providerExecutor;
    }

    @Override
    public String chat(ModelRequest request) {
        return providerExecutor.execute("chat", () -> {
            Fault fault = nextFault();
            throwIfImmediateFailure(fault);
            Duration delay = fault == Fault.TIMEOUT
                ? properties.timeoutDelay()
                : properties.firstTokenDelay().plus(properties.tokenInterval()
                    .multipliedBy(Math.max(0, properties.tokenCount() - 1L)));
            LockSupport.parkNanos(delay.toNanos());
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Simulated provider call interrupted");
            }
            return responseTokens().stream().reduce("", String::concat);
        });
    }

    @Override
    public Flux<String> stream(ModelRequest request) {
        return providerExecutor.stream("stream", () -> {
            Fault fault = nextFault();
            throwIfImmediateFailure(fault);
            Duration firstDelay = fault == Fault.TIMEOUT
                ? properties.timeoutDelay()
                : properties.firstTokenDelay();
            Flux<String> tokens = Flux.fromIterable(responseTokens())
                .index()
                .concatMap(indexed -> Mono.delay(indexed.getT1() == 0
                        ? firstDelay : properties.tokenInterval())
                    .thenReturn(indexed.getT2()));
            if (fault == Fault.AFTER_FIRST_TOKEN) {
                return tokens.take(1).concatWith(Flux.error(
                    new UncheckedIOException(new IOException("simulated connection reset"))));
            }
            return tokens;
        });
    }

    private List<String> responseTokens() {
        return IntStream.range(0, Math.max(1, properties.tokenCount()))
            .mapToObj(index -> index == 0 ? "Hammerly " : "token-" + index + " ")
            .toList();
    }

    private Fault nextFault() {
        long value = Math.floorMod(invocationSequence.incrementAndGet() * HASH_MULTIPLIER,
            1_000_000L);
        double sample = value / 1_000_000.0;
        double boundary = properties.rateLimitRate();
        if (sample < boundary) {
            return Fault.RATE_LIMIT;
        }
        boundary += properties.serverErrorRate();
        if (sample < boundary) {
            return Fault.SERVER_ERROR;
        }
        boundary += properties.timeoutRate();
        if (sample < boundary) {
            return Fault.TIMEOUT;
        }
        boundary += properties.connectionFailureRate();
        if (sample < boundary) {
            return Fault.CONNECTION;
        }
        boundary += properties.afterFirstTokenFailureRate();
        return sample < boundary ? Fault.AFTER_FIRST_TOKEN : Fault.NONE;
    }

    private void throwIfImmediateFailure(Fault fault) {
        switch (fault) {
            case RATE_LIMIT -> throw new AiProviderHttpException(429, "rate_limit_exceeded");
            case SERVER_ERROR -> throw new AiProviderHttpException(503, "service_unavailable");
            case CONNECTION -> throw new UncheckedIOException(
                new IOException("simulated connection failure"));
            default -> { }
        }
    }

    private enum Fault {
        NONE,
        RATE_LIMIT,
        SERVER_ERROR,
        TIMEOUT,
        CONNECTION,
        AFTER_FIRST_TOKEN
    }
}
