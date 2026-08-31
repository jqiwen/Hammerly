package com.hammerly.worker.knowledge;

import com.hammerly.worker.embedding.EmbeddingJobHandler;
import com.hammerly.worker.event.EmbeddingRequestedPayload;
import com.hammerly.worker.event.EventEnvelope;
import com.hammerly.worker.observability.WorkerMetrics;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeEmbeddingJobHandler implements EmbeddingJobHandler {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeEmbeddingJobHandler.class);
    private final KnowledgeDocumentRepository repository;
    private final DocumentChunker chunker;
    private final EmbeddingProvider embeddings;
    private final WorkerMetrics metrics;

    public KnowledgeEmbeddingJobHandler(KnowledgeDocumentRepository repository,
                                        DocumentChunker chunker,
                                        EmbeddingProvider embeddings,
                                        WorkerMetrics metrics) {
        this.repository = repository;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.metrics = metrics;
    }

    @Override
    public void handle(EventEnvelope event, EmbeddingRequestedPayload payload) {
        Object eventId = event == null ? "unknown" : event.eventId();
        var document = repository.find(payload.documentId())
            .orElseThrow(() -> new IllegalArgumentException("Knowledge document does not exist"));
        if ("READY".equals(document.status())) {
            log.info("knowledge_indexing_skipped eventId={} documentId={} reason=already_ready",
                eventId, document.id());
            return;
        }
        repository.markProcessing(document.id());
        log.info("knowledge_indexing_started eventId={} documentId={}",
            eventId, document.id());
        List<String> chunks = chunker.chunk(document.content());
        if (chunks.isEmpty()) throw new IllegalArgumentException("Knowledge document has no content");
        long startedAt = System.nanoTime();
        List<float[]> vectors = new ArrayList<>(chunks.size());
        for (String chunk : chunks) vectors.add(embeddings.embed(chunk));
        repository.replaceChunks(document, chunks, vectors);
        metrics.embeddingCompleted(chunks.size(), startedAt);
        log.info("knowledge_indexing_completed eventId={} documentId={} chunks={}",
            eventId, document.id(), chunks.size());
    }
}
