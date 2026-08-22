package com.hammerly.worker.embedding;

import com.hammerly.worker.event.EmbeddingRequestedPayload;
import com.hammerly.worker.event.EventEnvelope;

public interface EmbeddingJobHandler {
    void handle(EventEnvelope event, EmbeddingRequestedPayload payload);
}
