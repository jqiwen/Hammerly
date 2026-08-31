package com.hammerly.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.redis.RedisStateClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.ArgumentCaptor;

class PgVectorRagRetrievalServiceTest {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void embedsQueryAndReturnsBoundedDatabaseMatches() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RedisStateClient redis = mock(RedisStateClient.class);
        QueryEmbeddingProvider embeddings = mock(QueryEmbeddingProvider.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
        when(embeddings.embed("How do I bid?")).thenReturn(new float[] {1f, 0f, 0f});
        RagChunk match = new RagChunk("chunk-1", "document-1", "Bidding", "Support Guide",
            "A bid must be higher than the current bid.", 0.91);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(match));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PgVectorRagRetrievalService service = service(jdbc, redis, embeddings, registry,
            properties(Duration.ofSeconds(1)));

        RagResult result = service.retrieve("How do I bid?");

        assertThat(result.knowledgeVersion()).isEqualTo(7);
        assertThat(result.chunks()).containsExactly(match);
        verify(embeddings).embed("How do I bid?");
        assertThat(registry.get("rag.search.results").summary().count()).isEqualTo(1);
    }

    @Test
    void versionedRedisHitSkipsEmbeddingAndDatabaseSearch() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RedisStateClient redis = mock(RedisStateClient.class);
        QueryEmbeddingProvider embeddings = mock(QueryEmbeddingProvider.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(4L);
        RagResult cached = new RagResult(List.of(new RagChunk("chunk-2", "document-2",
            "Watchlists", "Guide",
            "A watchlist is a bookmark.", 0.88)), 4);
        when(redis.get(anyString())).thenReturn(new ObjectMapper().writeValueAsString(cached));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PgVectorRagRetrievalService service = service(jdbc, redis, embeddings, registry,
            properties(Duration.ofSeconds(1)));

        RagResult result = service.retrieve("How does a watchlist work?");

        assertThat(result.chunks()).isEqualTo(cached.chunks());
        assertThat(result.cacheHit()).isTrue();
        verify(embeddings, never()).embed(anyString());
        assertThat(registry.counter("rag.cache.hits").count()).isEqualTo(1);
    }

    @Test
    void slowEmbeddingDegradesWithinConfiguredTimeout() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RedisStateClient redis = mock(RedisStateClient.class);
        QueryEmbeddingProvider embeddings = input -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return new float[] {1f};
        };
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        PgVectorRagRetrievalService service = service(jdbc, redis, embeddings,
            new SimpleMeterRegistry(), properties(Duration.ofMillis(20)));

        long started = System.nanoTime();
        RagResult result = service.retrieve("question");

        assertThat(result.chunks()).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(250));
    }

    @Test
    @SuppressWarnings("unchecked")
    void appliesReadyStatusCosineThresholdAndTopKToPgvectorSearch() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RedisStateClient redis = mock(RedisStateClient.class);
        QueryEmbeddingProvider embeddings = input -> new float[] {1f, 0f};
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());
        PgVectorRagRetrievalService service = service(jdbc, redis, embeddings,
            new SimpleMeterRegistry(), properties(Duration.ofSeconds(1)));

        RagResult result = service.retrieve("How do I place a bid?");

        assertThat(result.chunks()).isEmpty();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), parameters.capture());
        assertThat(sql.getValue()).contains("hammerly.knowledge_chunks",
            "hammerly.knowledge_documents", "d.status = 'READY'", "embedding <=>", "LIMIT ?");
        assertThat(parameters.getValue()[2]).isEqualTo(0.25);
        assertThat(parameters.getValue()[4]).isEqualTo(4);
    }

    @Test
    void embeddingFailureFallsBackWithoutSearching() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RedisStateClient redis = mock(RedisStateClient.class);
        QueryEmbeddingProvider embeddings = mock(QueryEmbeddingProvider.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        when(embeddings.embed(anyString())).thenThrow(new IllegalStateException("provider down"));

        RagResult result = service(jdbc, redis, embeddings, new SimpleMeterRegistry(),
            properties(Duration.ofSeconds(1))).retrieve("question");

        assertThat(result.chunks()).isEmpty();
        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void databaseFailureFallsBackWithoutEmbedding() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RedisStateClient redis = mock(RedisStateClient.class);
        QueryEmbeddingProvider embeddings = mock(QueryEmbeddingProvider.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
            .thenThrow(new IllegalStateException("database down"));

        RagResult result = service(jdbc, redis, embeddings, new SimpleMeterRegistry(),
            properties(Duration.ofSeconds(1))).retrieve("question");

        assertThat(result.chunks()).isEmpty();
        verify(embeddings, never()).embed(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void localKnowledgeVersionCacheRemovesDatabaseRoundTripUntilTtlExpires() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RedisStateClient redis = mock(RedisStateClient.class);
        QueryEmbeddingProvider embeddings = input -> new float[] {1f, 0f};
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(9L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PgVectorRagRetrievalService service = service(jdbc, redis, embeddings, registry,
            properties(Duration.ofSeconds(1)));

        service.retrieve("first question");
        service.retrieve("second question");

        verify(jdbc, times(1)).queryForObject(
            "SELECT version FROM hammerly.knowledge_base_state WHERE id = 1", Long.class);
        assertThat(registry.get("rag.kb_version").tag("source", "db_load")
            .counter().count()).isEqualTo(1);
        assertThat(registry.get("rag.kb_version").tag("source", "local_hit")
            .counter().count()).isEqualTo(1);
    }

    @Test
    void localKnowledgeVersionNeverLoadsDatabaseAndOnlyReturnsWarmSnapshot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RedisStateClient redis = mock(RedisStateClient.class);
        QueryEmbeddingProvider embeddings = input -> new float[] {1f, 0f};
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(12L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());
        PgVectorRagRetrievalService service = service(jdbc, redis, embeddings,
            new SimpleMeterRegistry(), properties(Duration.ofSeconds(1)));

        assertThat(service.localKnowledgeVersion().available()).isFalse();
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class));

        service.retrieve("warm the version");

        assertThat(service.localKnowledgeVersion().available()).isTrue();
        assertThat(service.localKnowledgeVersion().value()).isEqualTo(12);
        verify(jdbc, times(1)).queryForObject(
            "SELECT version FROM hammerly.knowledge_base_state WHERE id = 1", Long.class);
    }

    private PgVectorRagRetrievalService service(JdbcTemplate jdbc, RedisStateClient redis,
                                                 QueryEmbeddingProvider embeddings,
                                                 SimpleMeterRegistry registry,
                                                 RagProperties properties) {
        return new PgVectorRagRetrievalService(jdbc, embeddings, redis,
            new ObjectMapper().findAndRegisterModules(), properties, new AiMetrics(registry), executor);
    }

    private RagProperties properties(Duration timeout) {
        return new RagProperties(true, 4, 0.25, Duration.ofMinutes(5),
            Duration.ofSeconds(45), timeout,
            "deterministic", "deterministic-v1", 1536, "https://api.openai.com", "",
            "jdbc:postgresql://localhost/test", "", "", "disable");
    }
}
