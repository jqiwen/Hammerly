package com.hammerly.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.redis.InMemoryRedisStateClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PgVectorRagRetrievalIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("DROP SCHEMA IF EXISTS hammerly CASCADE");
        jdbc.execute("CREATE SCHEMA hammerly");
        jdbc.execute("""
            CREATE TABLE hammerly.knowledge_documents (
                id UUID PRIMARY KEY, title TEXT NOT NULL, status TEXT NOT NULL
            )
            """);
        jdbc.execute("""
            CREATE TABLE hammerly.knowledge_chunks (
                id UUID PRIMARY KEY,
                document_id UUID NOT NULL REFERENCES hammerly.knowledge_documents(id),
                content TEXT NOT NULL,
                embedding vector(3) NOT NULL,
                metadata JSONB NOT NULL DEFAULT '{}'::jsonb
            )
            """);
        jdbc.execute("""
            CREATE INDEX knowledge_chunks_embedding_hnsw_idx
            ON hammerly.knowledge_chunks USING hnsw (embedding vector_cosine_ops)
            """);
        jdbc.execute("""
            CREATE TABLE hammerly.knowledge_base_state (
                id SMALLINT PRIMARY KEY, version BIGINT NOT NULL
            )
            """);
        jdbc.update("INSERT INTO hammerly.knowledge_base_state (id, version) VALUES (1, 8)");
    }

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void retrievesRelevantReadyBiddingChunkWithCosineSimilarity() {
        insertDocument("Hammerly Support Guide", "READY", "Bidding",
            "Users place bids from an auction detail page while signed in.", "[1,0,0]");
        insertDocument("Hammerly Support Guide", "READY", "Watchlists",
            "A watchlist bookmarks an auction.", "[0,1,0]");
        insertDocument("Draft Guide", "PENDING", "Unpublished Bidding",
            "This pending document must never be retrieved.", "[1,0,0]");
        QueryEmbeddingProvider embeddings = question -> new float[] {1f, 0f, 0f};
        RagProperties properties = new RagProperties(true, 4, 0.25, Duration.ofMinutes(5),
            Duration.ofSeconds(45), Duration.ofSeconds(2), "deterministic", "deterministic-v1", 3,
            "https://api.openai.com", "", POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
            POSTGRES.getPassword(), "disable");
        PgVectorRagRetrievalService service = new PgVectorRagRetrievalService(
            jdbc, embeddings, new InMemoryRedisStateClient(),
            new ObjectMapper().findAndRegisterModules(), properties,
            new AiMetrics(new SimpleMeterRegistry()), executor);

        RagResult result = service.retrieve("How do I place a bid?");

        assertThat(result.knowledgeVersion()).isEqualTo(8);
        assertThat(result.chunks()).singleElement().satisfies(chunk -> {
            assertThat(chunk.title()).isEqualTo("Bidding");
            assertThat(chunk.source()).isEqualTo("Hammerly Support Guide");
            assertThat(chunk.content()).contains("auction detail page");
            assertThat(chunk.similarity()).isGreaterThan(0.99);
        });
    }

    private void insertDocument(String title, String status, String sectionTitle,
                                String content, String vector) {
        UUID documentId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO hammerly.knowledge_documents (id, title, status) VALUES (?, ?, ?)
            """,
            documentId, title, status);
        jdbc.update("""
            INSERT INTO hammerly.knowledge_chunks (id, document_id, content, embedding, metadata)
            VALUES (?, ?, ?, CAST(? AS vector), CAST(? AS jsonb))
            """, UUID.randomUUID(), documentId, content, vector,
            "{\"sectionTitle\":\"" + sectionTitle + "\"}");
    }
}
