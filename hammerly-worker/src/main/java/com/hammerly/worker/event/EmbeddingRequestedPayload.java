package com.hammerly.worker.event;

public record EmbeddingRequestedPayload(
    String sourceType,
    String sourceId,
    String content
) {
}
