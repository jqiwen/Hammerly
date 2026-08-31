package com.hammerly.ai.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.ai.config.AiStateProperties;
import com.hammerly.ai.config.HammerlySystemPrompt;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.rag.RagKnowledgeVersion;
import com.hammerly.ai.rag.RagProperties;
import com.hammerly.ai.rag.RagRetrievalService;
import com.hammerly.ai.rag.RagSource;
import com.hammerly.ai.redis.RedisStateClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RedisGroundedFaqCache implements GroundedFaqCache {
    private static final Logger log = LoggerFactory.getLogger(RedisGroundedFaqCache.class);
    private static final String KEY_PREFIX = "hammerly:ai:grounded-faq:v1:";

    private final RedisStateClient redis;
    private final ObjectMapper objectMapper;
    private final AiStateProperties stateProperties;
    private final RagRetrievalService rag;
    private final AiMetrics metrics;
    private final String model;
    private final String configVersion;

    public RedisGroundedFaqCache(RedisStateClient redis, ObjectMapper objectMapper,
                                 AiStateProperties stateProperties, RagRetrievalService rag,
                                 RagProperties ragProperties, HammerlySystemPrompt systemPrompt,
                                 AiMetrics metrics,
                                 @Value("${spring.ai.openai-sdk.chat.options.model}") String model) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.stateProperties = stateProperties;
        this.rag = rag;
        this.metrics = metrics;
        this.model = model;
        this.configVersion = sha256("faq-v1\n" + systemPrompt.content() + "\n"
            + ragProperties.topK() + "\n" + ragProperties.similarityThreshold() + "\n"
            + ragProperties.embeddingModel());
    }

    @Override
    public GroundedFaqCacheProbe lookup(String question) {
        long startedAt = System.nanoTime();
        RagKnowledgeVersion version = rag.localKnowledgeVersion();
        if (!version.available()) {
            metrics.groundedFaqCacheMiss();
            return new GroundedFaqCacheProbe(Optional.empty(), 0,
                elapsedMillis(startedAt), false);
        }
        try {
            String json = redis.get(key(question, version.value()));
            if (!StringUtils.hasText(json)) {
                metrics.groundedFaqCacheMiss();
                return new GroundedFaqCacheProbe(Optional.empty(), version.value(),
                    elapsedMillis(startedAt), true);
            }
            GroundedFaqCacheEntry entry = objectMapper.readValue(json,
                GroundedFaqCacheEntry.class);
            if (!valid(entry, version.value())) {
                metrics.groundedFaqCacheMiss();
                return new GroundedFaqCacheProbe(Optional.empty(), version.value(),
                    elapsedMillis(startedAt), true);
            }
            metrics.groundedFaqCacheHit();
            return new GroundedFaqCacheProbe(Optional.of(entry), version.value(),
                elapsedMillis(startedAt), true);
        } catch (Exception exception) {
            metrics.groundedFaqCacheMiss();
            metrics.redisError("grounded_faq_cache_read");
            log.warn("Grounded FAQ cache read failed; treating as a miss errorType={}",
                rootCauseName(exception));
            return new GroundedFaqCacheProbe(Optional.empty(), version.value(),
                elapsedMillis(startedAt), true);
        }
    }

    @Override
    public void put(String question, long knowledgeBaseVersion, String answer,
                    List<RagSource> sources) {
        if (knowledgeBaseVersion <= 0 || !StringUtils.hasText(answer) || sources.isEmpty()) return;
        GroundedFaqCacheEntry entry = new GroundedFaqCacheEntry(answer, sources,
            knowledgeBaseVersion, model, configVersion);
        try {
            redis.set(key(question, knowledgeBaseVersion), objectMapper.writeValueAsString(entry),
                stateProperties.responseCache().ttl());
        } catch (Exception exception) {
            metrics.redisError("grounded_faq_cache_write");
            log.warn("Grounded FAQ cache write failed; response will still be returned errorType={}",
                rootCauseName(exception));
        }
    }

    private boolean valid(GroundedFaqCacheEntry entry, long knowledgeBaseVersion) {
        return entry != null && StringUtils.hasText(entry.answer()) && !entry.sources().isEmpty()
            && entry.knowledgeBaseVersion() == knowledgeBaseVersion
            && model.equals(entry.model()) && configVersion.equals(entry.configVersion());
    }

    private String key(String question, long knowledgeBaseVersion) {
        MessageDigest digest = digest();
        add(digest, normalize(question));
        add(digest, Long.toString(knowledgeBaseVersion));
        add(digest, model);
        add(digest, configVersion);
        return KEY_PREFIX + HexFormat.of().formatHex(digest.digest());
    }

    private String normalize(String question) {
        return question.strip().toLowerCase().replaceAll("\\s+", " ");
    }

    private void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private String sha256(String value) {
        return HexFormat.of().formatHex(digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String rootCauseName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getClass().getSimpleName();
    }
}
