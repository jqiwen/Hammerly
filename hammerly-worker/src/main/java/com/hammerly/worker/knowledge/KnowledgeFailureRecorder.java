package com.hammerly.worker.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.worker.event.EmbeddingRequestedPayload;
import com.hammerly.worker.event.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeFailureRecorder {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeFailureRecorder.class);
    private final ObjectMapper objectMapper;
    private final KnowledgeDocumentRepository repository;

    public KnowledgeFailureRecorder(ObjectMapper objectMapper, KnowledgeDocumentRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    public void recordIfKnowledgeEvent(String json, Throwable failure) {
        try {
            EventEnvelope event = objectMapper.readValue(json, EventEnvelope.class);
            if (!"embedding.requested".equals(event.eventType())) return;
            EmbeddingRequestedPayload payload = objectMapper.treeToValue(
                event.payload(), EmbeddingRequestedPayload.class);
            repository.markFailed(payload.documentId(), "Embedding failed after retries ("
                + rootCause(failure).getClass().getSimpleName() + ")");
        } catch (Exception exception) {
            // The DLT record remains the source of truth when malformed input cannot identify a document.
            log.warn("Could not associate dead-letter event with a knowledge document errorType={}",
                rootCause(exception).getClass().getSimpleName());
        }
    }

    private Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }
}
