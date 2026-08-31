package com.hammerly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hammerly.ai.rag.OpenAiQueryEmbeddingProvider;
import com.hammerly.ai.rag.PgVectorRagRetrievalService;
import com.hammerly.ai.rag.QueryEmbeddingProvider;
import com.hammerly.ai.rag.RagRetrievalService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "OPENAI_API_KEY=",
    "spring.ai.openai-sdk.api-key=",
    "hammerly.redis.enabled=false",
    "hammerly.kafka.enabled=false",
    "hammerly.ai.rag.enabled=true",
    "hammerly.ai.rag.embedding-provider=openai",
    "hammerly.ai.rag.datasource-url=jdbc:postgresql://127.0.0.1:1/rag-unavailable?sslmode=disable",
    "hammerly.ai.rag.datasource-username=unused",
    "hammerly.ai.rag.datasource-password=unused",
    "hammerly.ai.rag.datasource-ssl-mode=disable",
    "hammerly.ai.rag.timeout=250ms",
    "management.health.db.enabled=false",
    "hammerly.ai.loadtest-provider.first-token-delay=0ms",
    "hammerly.ai.loadtest-provider.token-interval=0ms",
    "hammerly.ai.loadtest-provider.token-count=2"
})
@ActiveProfiles("loadtest")
@AutoConfigureMockMvc
class RagUnavailableStartupTest {
    private static final String CONVERSATION_ID = "a8aeac65-8ef9-46b2-b63d-96c23053f0d4";

    @Autowired
    MockMvc mvc;

    @Autowired
    RagRetrievalService rag;

    @Autowired
    QueryEmbeddingProvider embeddings;

    @Test
    void unavailableDatabaseDoesNotBlockStartupHealthOrChat() throws Exception {
        assertThat(rag).isInstanceOf(PgVectorRagRetrievalService.class);

        long started = System.nanoTime();
        assertThat(rag.retrieve("How do I bid?").chunks()).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));

        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        mvc.perform(post("/internal/ai/chat")
                .header("X-Hammerly-User-Id", "rag-outage-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"Can I still chat?","history":[],"conversationId":"%s"}
                    """.formatted(CONVERSATION_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Hammerly token-1 "));
    }

    @Test
    void openAiEmbeddingProviderStartsWithoutKeyAndFailsClearlyOnUse() {
        assertThat(embeddings).isInstanceOf(OpenAiQueryEmbeddingProvider.class);
        assertThatThrownBy(() -> embeddings.embed("question"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OPENAI_API_KEY")
            .hasMessageContaining("embedding is requested");
    }
}
