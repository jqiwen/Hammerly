package com.hammerly.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiQueryEmbeddingProviderTest {
    @Test
    void sendsConfiguredModelAndReturnsExpectedDimension() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
            .andExpect(content().json("""
                {"model":"text-embedding-3-small","input":"How do I bid?","dimensions":3}
                """))
            .andRespond(withSuccess("""
                {"data":[{"embedding":[0.1,0.2,0.3]}]}
                """, MediaType.APPLICATION_JSON));
        OpenAiQueryEmbeddingProvider provider = new OpenAiQueryEmbeddingProvider(
            properties(3), builder.build());

        float[] embedding = provider.embed("How do I bid?");

        assertThat(embedding).containsExactly(0.1f, 0.2f, 0.3f);
        server.verify();
    }

    @Test
    void rejectsVectorsWithTheWrongDimension() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
            .andRespond(withSuccess("""
                {"data":[{"embedding":[0.1,0.2]}]}
                """, MediaType.APPLICATION_JSON));
        OpenAiQueryEmbeddingProvider provider = new OpenAiQueryEmbeddingProvider(
            properties(3), builder.build());

        assertThatThrownBy(() -> provider.embed("question"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("invalid vector");
    }

    private RagProperties properties(int dimension) {
        return new RagProperties(true, 4, 0.25, Duration.ofMinutes(5), Duration.ofSeconds(2),
            "openai", "text-embedding-3-small", dimension, "https://api.openai.test", "key",
            "jdbc:postgresql://localhost/test", "", "", "disable");
    }
}
