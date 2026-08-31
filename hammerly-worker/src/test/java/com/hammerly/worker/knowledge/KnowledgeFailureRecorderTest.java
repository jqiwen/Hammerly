package com.hammerly.worker.knowledge;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeFailureRecorderTest {
    @Test
    void exhaustedEmbeddingFailureStoresOnlySanitizedType() {
        KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
        KnowledgeFailureRecorder recorder = new KnowledgeFailureRecorder(
            new ObjectMapper().findAndRegisterModules(), repository);
        UUID documentId = UUID.fromString("b29bd72b-a2d5-4938-90f0-151867ac4c7a");

        recorder.recordIfKnowledgeEvent("""
            {"eventId":"61a96d7d-2e0c-4ee0-8222-90ca5310a788",
             "eventType":"embedding.requested","eventVersion":1,
             "occurredAt":"2026-08-30T12:00:00Z","producer":"hammerly-core",
             "correlationId":"869bc0a5-da81-40ce-970f-3fb6886d11df",
             "aggregateId":"b29bd72b-a2d5-4938-90f0-151867ac4c7a",
             "payload":{"documentId":"b29bd72b-a2d5-4938-90f0-151867ac4c7a"}}
            """, new RuntimeException("api-key=must-not-be-stored"));

        verify(repository).markFailed(documentId,
            "Embedding failed after retries (RuntimeException)");
    }

    @Test
    void unrelatedEventDoesNotChangeDocumentState() {
        KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
        KnowledgeFailureRecorder recorder = new KnowledgeFailureRecorder(
            new ObjectMapper().findAndRegisterModules(), repository);

        recorder.recordIfKnowledgeEvent("""
            {"eventId":"61a96d7d-2e0c-4ee0-8222-90ca5310a788",
             "eventType":"message.created","eventVersion":1,
             "occurredAt":"2026-08-30T12:00:00Z","producer":"hammerly-ai",
             "correlationId":"869bc0a5-da81-40ce-970f-3fb6886d11df","payload":{}}
            """, new RuntimeException("failure"));

        verifyNoInteractions(repository);
    }
}
