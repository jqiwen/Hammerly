package com.hammerly.backend.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.backend.exception.ApiException;
import com.hammerly.backend.knowledge.KnowledgeDtos.CreateDocumentRequest;
import com.hammerly.backend.knowledge.KnowledgeDtos.DocumentResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {
    private final KnowledgeRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String jobsTopic;

    public KnowledgeService(KnowledgeRepository repository, ObjectMapper objectMapper,
                            @Value("${hammerly.kafka.jobs-topic}") String jobsTopic) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
        this.jobsTopic = jobsTopic;
    }

    @Transactional
    public DocumentResponse create(CreateDocumentRequest request) {
        String title = request.title().strip();
        String source = request.source().strip();
        String content = request.content().strip();
        String hash = sha256(content);
        var existing = repository.findBySourceAndHash(source, hash);
        if (existing.isPresent()) return existing.orElseThrow();

        UUID documentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = clock.instant();
        repository.insertDocument(documentId, title, source, content, hash);
        try {
            Map<String, Object> envelope = Map.of(
                "eventId", eventId,
                "eventType", "embedding.requested",
                "eventVersion", 1,
                "occurredAt", occurredAt,
                "producer", "hammerly-core",
                "correlationId", UUID.randomUUID(),
                "aggregateId", documentId,
                "payload", Map.of("documentId", documentId)
            );
            repository.insertOutbox(eventId, documentId, jobsTopic,
                objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize knowledge ingestion event", exception);
        }
        return repository.findById(documentId).orElseThrow();
    }

    public DocumentResponse get(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Knowledge document not found"));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
