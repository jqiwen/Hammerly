package com.hammerly.ai.rag;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hammerly.ai.rag")
public record RagProperties(
    boolean enabled,
    int topK,
    double similarityThreshold,
    Duration cacheTtl,
    Duration knowledgeVersionCacheTtl,
    Duration timeout,
    String embeddingProvider,
    String embeddingModel,
    int embeddingDimension,
    String openaiBaseUrl,
    String openaiApiKey,
    String datasourceUrl,
    String datasourceUsername,
    String datasourcePassword,
    String datasourceSslMode
) {
}
