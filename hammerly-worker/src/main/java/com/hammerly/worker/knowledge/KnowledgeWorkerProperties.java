package com.hammerly.worker.knowledge;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hammerly.knowledge")
public record KnowledgeWorkerProperties(
    int chunkTokens,
    int chunkOverlapTokens,
    String embeddingProvider,
    String embeddingModel,
    int embeddingDimension,
    String openaiBaseUrl,
    String openaiApiKey,
    Duration requestTimeout
) {
}
