package com.hammerly.backend.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@EnableConfigurationProperties(MarketplaceCacheProperties.class)
public class MarketplaceCache {
    private static final long FAILURE_BACKOFF_NANOS = Duration.ofSeconds(1).toNanos();
    private static final Logger log = LoggerFactory.getLogger(MarketplaceCache.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final String AUCTION_PREFIX = "hammerly:marketplace:auction:";
    private static final String TOP_KEY = "hammerly:marketplace:list:top:v1";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MarketplaceCacheProperties properties;
    private final MeterRegistry metrics;
    private final ExecutorService redisExecutor;
    private final AtomicLong unavailableUntilNanos = new AtomicLong();

    public MarketplaceCache(ObjectProvider<StringRedisTemplate> redis,
                            ObjectMapper objectMapper,
                            MarketplaceCacheProperties properties,
                            MeterRegistry metrics) {
        this.redis = redis.getIfAvailable();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
        this.redisExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public Optional<Map<String, Object>> getAuction(long auctionId) {
        return get(AUCTION_PREFIX + auctionId);
    }

    public void putAuction(long auctionId, Map<String, Object> value) {
        put(AUCTION_PREFIX + auctionId, value, properties.auctionTtl());
    }

    public Optional<Map<String, Object>> getTop() {
        return get(TOP_KEY);
    }

    public void putTop(Map<String, Object> value) {
        put(TOP_KEY, value, properties.listingTtl());
    }

    public void invalidateAuctionAfterCommit(long auctionId) {
        afterCommit(() -> delete(AUCTION_PREFIX + auctionId));
    }

    public void invalidateListingsAfterCommit() {
        afterCommit(() -> delete(TOP_KEY));
    }

    private Optional<Map<String, Object>> get(String key) {
        if (!properties.enabled() || redis == null) {
            miss();
            return Optional.empty();
        }
        long startedAt = System.nanoTime();
        try {
            String json = bounded(() -> redis.opsForValue().get(key));
            if (json == null) {
                miss();
                return Optional.empty();
            }
            metrics.counter("marketplace.cache.hits").increment();
            return Optional.of(objectMapper.readValue(json, MAP_TYPE));
        } catch (RuntimeException | JsonProcessingException exception) {
            error("read", exception);
            miss();
            return Optional.empty();
        } finally {
            operationCompleted("read", startedAt);
        }
    }

    private void put(String key, Map<String, Object> value, Duration ttl) {
        if (!properties.enabled() || redis == null) return;
        long startedAt = System.nanoTime();
        try {
            String json = objectMapper.writeValueAsString(value);
            bounded(() -> {
                redis.opsForValue().set(key, json, ttl);
                return null;
            });
        } catch (RuntimeException | JsonProcessingException exception) {
            error("write", exception);
        } finally {
            operationCompleted("write", startedAt);
        }
    }

    private void delete(String key) {
        if (!properties.enabled() || redis == null) return;
        long startedAt = System.nanoTime();
        try {
            bounded(() -> redis.delete(key));
        } catch (RuntimeException exception) {
            error("invalidate", exception);
        } finally {
            operationCompleted("invalidate", startedAt);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void miss() {
        metrics.counter("marketplace.cache.misses").increment();
    }

    private void error(String operation, Exception exception) {
        metrics.counter("marketplace.cache.errors", "operation", operation).increment();
        log.warn("Marketplace Redis cache {} failed; PostgreSQL remains authoritative errorType={}",
            operation, exception.getClass().getSimpleName());
    }

    private void operationCompleted(String operation, long startedAtNanos) {
        metrics.timer("marketplace.cache.operation.duration", "operation", operation)
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }

    private <T> T bounded(Supplier<T> operation) {
        long now = System.nanoTime();
        if (now < unavailableUntilNanos.get()) {
            metrics.counter("marketplace.cache.short_circuit").increment();
            throw new IllegalStateException("Marketplace Redis cache is temporarily unavailable");
        }
        Future<T> future = redisExecutor.submit(operation::get);
        try {
            T result = future.get(properties.operationTimeout().toMillis(), TimeUnit.MILLISECONDS);
            unavailableUntilNanos.set(0);
            return result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            unavailableUntilNanos.set(System.nanoTime() + FAILURE_BACKOFF_NANOS);
            throw new IllegalStateException("Marketplace Redis cache operation timed out", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Marketplace Redis cache operation interrupted", exception);
        } catch (ExecutionException exception) {
            unavailableUntilNanos.set(System.nanoTime() + FAILURE_BACKOFF_NANOS);
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Marketplace Redis cache operation failed", exception.getCause());
        }
    }

    @PreDestroy
    void closeExecutor() {
        redisExecutor.close();
    }
}
