package com.hammerly.worker.knowledge;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hammerly.worker.event.EmbeddingRequestedPayload;
import com.hammerly.worker.observability.WorkerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeEmbeddingJobHandlerTest {
    private static final UUID DOCUMENT_ID = UUID.fromString(
        "b29bd72b-a2d5-4938-90f0-151867ac4c7a");

    @Test
    void chunksEmbedsAndAtomicallyReplacesNonReadyDocument() {
        KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
        DocumentChunker chunker = mock(DocumentChunker.class);
        EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
        KnowledgeDocumentRepository.Document document = new KnowledgeDocumentRepository.Document(
            DOCUMENT_ID, "Guide", "support.md", "auction bidding guide", "PENDING");
        when(repository.find(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(chunker.chunk(document.content())).thenReturn(List.of("auction bidding", "bidding guide"));
        float[] first = {1f, 0f};
        float[] second = {0f, 1f};
        when(embeddings.embed("auction bidding")).thenReturn(first);
        when(embeddings.embed("bidding guide")).thenReturn(second);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KnowledgeEmbeddingJobHandler handler = new KnowledgeEmbeddingJobHandler(repository, chunker,
            embeddings, new WorkerMetrics(registry));

        handler.handle(null, new EmbeddingRequestedPayload(DOCUMENT_ID));

        verify(repository).markProcessing(DOCUMENT_ID);
        verify(repository).replaceChunks(document, List.of("auction bidding", "bidding guide"),
            List.of(first, second));
        org.assertj.core.api.Assertions.assertThat(registry.get("hammerly.worker.embedding.duration")
            .timer().count()).isEqualTo(1);
    }

    @Test
    void repeatedDeliveryDoesNotReindexReadyDocument() {
        KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
        DocumentChunker chunker = mock(DocumentChunker.class);
        EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
        when(repository.find(DOCUMENT_ID)).thenReturn(Optional.of(
            new KnowledgeDocumentRepository.Document(DOCUMENT_ID, "Guide", "support.md",
                "content", "READY")));
        KnowledgeEmbeddingJobHandler handler = new KnowledgeEmbeddingJobHandler(repository, chunker,
            embeddings, new WorkerMetrics(new SimpleMeterRegistry()));

        handler.handle(null, new EmbeddingRequestedPayload(DOCUMENT_ID));

        verify(repository, never()).markProcessing(DOCUMENT_ID);
        verify(repository, never()).replaceChunks(
            org.mockito.ArgumentMatchers.any(), anyList(), anyList());
        verify(chunker, never()).chunk(org.mockito.ArgumentMatchers.anyString());
    }
}
