package com.hammerly.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.redis.RedisStateClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "hammerly.ai.rag", name = "enabled", havingValue = "true")
public class PgVectorRagRetrievalService implements RagRetrievalService {
    private static final Logger log = LoggerFactory.getLogger(PgVectorRagRetrievalService.class);
    private static final String CACHE_PREFIX = "hammerly:rag:retrieval:v1:";
    private final JdbcTemplate jdbc;
    private final QueryEmbeddingProvider embeddings;
    private final RedisStateClient redis;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;
    private final AiMetrics metrics;
    private final ExecutorService executor;

    public PgVectorRagRetrievalService(JdbcTemplate ragJdbcTemplate,
                                       QueryEmbeddingProvider embeddings,
                                       RedisStateClient redis,
                                       ObjectMapper objectMapper,
                                       RagProperties properties,
                                       AiMetrics metrics,
                                       ExecutorService ragExecutor) {
        this.jdbc = ragJdbcTemplate;
        this.embeddings = embeddings;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
        this.executor = ragExecutor;
    }

    @Override
    public RagResult retrieve(String query) {
        Future<RagResult> future = executor.submit(() -> retrieveBounded(query));
        try {
            return future.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            future.cancel(true);
            metrics.ragFailure("timeout_or_unavailable");
            log.warn("RAG retrieval degraded to ungrounded support errorType={}",
                rootCause(exception).getClass().getSimpleName());
            return RagResult.empty();
        }
    }

    private RagResult retrieveBounded(String query) {
        try {
            Long versionValue = jdbc.queryForObject(
                "SELECT version FROM knowledge_base_state WHERE id = 1", Long.class);
            long version = versionValue == null ? 0 : versionValue;
            String cacheKey = cacheKey(query, version);
            Optional<RagResult> cached = readCache(cacheKey);
            if (cached.isPresent()) return cached.orElseThrow();

            long embeddingStarted = System.nanoTime();
            float[] vector = embeddings.embed(query);
            metrics.ragEmbeddingCompleted(embeddingStarted);

            long searchStarted = System.nanoTime();
            String literal = vectorLiteral(vector);
            List<RagChunk> chunks = jdbc.query("""
                SELECT c.id::text AS chunk_id, d.title, d.source, c.content,
                       1 - (c.embedding <=> CAST(? AS vector)) AS similarity
                FROM knowledge_chunks c
                JOIN knowledge_documents d ON d.id = c.document_id
                WHERE d.status = 'READY'
                  AND 1 - (c.embedding <=> CAST(? AS vector)) >= ?
                ORDER BY c.embedding <=> CAST(? AS vector)
                LIMIT ?
                """, (rs, row) -> new RagChunk(rs.getString("chunk_id"), rs.getString("title"),
                    rs.getString("source"), rs.getString("content"), rs.getDouble("similarity")),
                literal, literal, properties.similarityThreshold(), literal, properties.topK());
            metrics.ragSearchCompleted(searchStarted, chunks.size());
            RagResult result = new RagResult(chunks, version);
            writeCache(cacheKey, result);
            return result;
        } catch (RuntimeException exception) {
            metrics.ragFailure("retrieval");
            log.warn("RAG retrieval failed fast; continuing without knowledge errorType={}",
                rootCause(exception).getClass().getSimpleName());
            return RagResult.empty();
        }
    }

    private Optional<RagResult> readCache(String key) {
        try {
            String json = redis.get(key);
            if (json == null) {
                metrics.ragCacheMiss();
                return Optional.empty();
            }
            RagResult value = objectMapper.readValue(json, RagResult.class);
            metrics.ragCacheHit();
            metrics.ragSearchResults(value.chunks().size());
            return Optional.of(value);
        } catch (Exception exception) {
            metrics.ragCacheMiss();
            metrics.redisError("rag_cache_read");
            return Optional.empty();
        }
    }

    private void writeCache(String key, RagResult value) {
        try {
            redis.set(key, objectMapper.writeValueAsString(value), properties.cacheTtl());
        } catch (Exception exception) {
            metrics.redisError("rag_cache_write");
        }
    }

    private String cacheKey(String query, long version) {
        MessageDigest digest = digest();
        add(digest, normalize(query));
        add(digest, Long.toString(version));
        add(digest, Integer.toString(properties.topK()));
        add(digest, Double.toString(properties.similarityThreshold()));
        add(digest, properties.embeddingModel());
        return CACHE_PREFIX + HexFormat.of().formatHex(digest.digest());
    }

    private void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private String normalize(String query) {
        return query.strip().toLowerCase().replaceAll("\\s+", " ");
    }

    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder(vector.length * 8).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) value.append(',');
            value.append(Float.toString(vector[index]));
        }
        return value.append(']').toString();
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }
}
