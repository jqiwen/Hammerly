package com.hammerly.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "hammerly.ai.rag", name = "embedding-provider",
    havingValue = "openai")
public class OpenAiQueryEmbeddingProvider implements QueryEmbeddingProvider {
    private final RagProperties properties;
    private final RestClient client;

    @Autowired
    public OpenAiQueryEmbeddingProvider(RagProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.openaiBaseUrl())
            .requestFactory(requestFactory);
        if (StringUtils.hasText(properties.openaiApiKey())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.openaiApiKey());
        }
        this.client = builder.build();
    }

    OpenAiQueryEmbeddingProvider(RagProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public float[] embed(String input) {
        if (!StringUtils.hasText(properties.openaiApiKey())) {
            throw new IllegalStateException(
                "OPENAI_API_KEY is required when an OpenAI query embedding is requested");
        }
        JsonNode response = client.post().uri("/v1/embeddings")
            .body(Map.of("model", properties.embeddingModel(), "input", input,
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
