package com.hammerly.worker.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "hammerly.knowledge", name = "embedding-provider",
    havingValue = "openai")
public class OpenAiEmbeddingProvider implements EmbeddingProvider {
    private final KnowledgeWorkerProperties properties;
    private final RestClient client;

    public OpenAiEmbeddingProvider(KnowledgeWorkerProperties properties) {
        this.properties = properties;
        this.client = RestClient.builder().baseUrl(properties.openaiBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.openaiApiKey())
            .build();
        if (!StringUtils.hasText(properties.openaiApiKey())) {
            throw new IllegalStateException("OPENAI_API_KEY is required for the OpenAI embedding provider");
        }
    }

    @Override
    public float[] embed(String content) {
        JsonNode response = client.post().uri("/v1/embeddings")
            .body(Map.of("model", properties.embeddingModel(), "input", content,
                "dimensions", properties.embeddingDimension()))
            .retrieve().body(JsonNode.class);
        JsonNode values = response == null ? null : response.path("data").path(0).path("embedding");
        if (values == null || !values.isArray() || values.size() != properties.embeddingDimension()) {
            throw new IllegalStateException("Embedding provider returned an invalid vector");
        }
        float[] vector = new float[values.size()];
        for (int index = 0; index < values.size(); index++) vector[index] = values.get(index).floatValue();
        return vector;
    }
}
