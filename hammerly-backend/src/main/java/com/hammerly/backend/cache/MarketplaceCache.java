package com.hammerly.backend.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
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
    private static final Logger log = LoggerFactory.getLogger(MarketplaceCache.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final String AUCTION_PREFIX = "hammerly:marketplace:auction:";
    private static final String TOP_KEY = "hammerly:marketplace:list:top:v1";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MarketplaceCacheProperties properties;
    private final MeterRegistry metrics;

    public MarketplaceCache(ObjectProvider<StringRedisTemplate> redis,
                            ObjectMapper objectMapper,
                            MarketplaceCacheProperties properties,
                            MeterRegistry metrics) {
        this.redis = redis.getIfAvailable();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
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
        try {
            String json = redis.opsForValue().get(key);
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
        }
    }

    private void put(String key, Map<String, Object> value, Duration ttl) {
        if (!properties.enabled() || redis == null) return;
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (RuntimeException | JsonProcessingException exception) {
            error("write", exception);
        }
    }

    private void delete(String key) {
        if (!properties.enabled() || redis == null) return;
        try {
            redis.delete(key);
        } catch (RuntimeException exception) {
            error("invalidate", exception);
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
}
