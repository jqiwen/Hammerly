package com.hammerly.worker.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class KnowledgeDocumentRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public KnowledgeDocumentRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<Document> find(UUID id) {
        return jdbc.query("""
            SELECT id, title, source, content, status FROM knowledge_documents WHERE id = ?
            """, (rs, row) -> new Document(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("source"), rs.getString("content"), rs.getString("status")), id)
            .stream().findFirst();
    }

    public void markProcessing(UUID id) {
        jdbc.update("""
            UPDATE knowledge_documents SET status = 'PROCESSING', failure_message = NULL,
                updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status <> 'READY'
            """, id);
    }

    @Transactional
    public void replaceChunks(Document document, List<String> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) throw new IllegalArgumentException("Chunk/vector count mismatch");
        jdbc.update("DELETE FROM knowledge_chunks WHERE document_id = ?", document.id());
        for (int index = 0; index < chunks.size(); index++) {
            UUID chunkId = UUID.nameUUIDFromBytes((document.id() + ":" + index)
                .getBytes(StandardCharsets.UTF_8));
            String metadata = metadata(document, index, chunks.get(index));
            String vector = vectorLiteral(embeddings.get(index));
            int chunkIndex = index;
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO knowledge_chunks
                        (id, document_id, chunk_index, content, embedding, metadata)
                    VALUES (?, ?, ?, ?, CAST(? AS vector), CAST(? AS jsonb))
                    """);
                statement.setObject(1, chunkId);
                statement.setObject(2, document.id());
                statement.setInt(3, chunkIndex);
                statement.setString(4, chunks.get(chunkIndex));
                statement.setString(5, vector);
                statement.setString(6, metadata);
                return statement;
            });
        }
        jdbc.update("""
            UPDATE knowledge_documents SET status = 'READY', failure_message = NULL,
                updated_at = CURRENT_TIMESTAMP WHERE id = ?
            """, document.id());
        jdbc.update("""
            UPDATE knowledge_base_state SET version = version + 1,
                updated_at = CURRENT_TIMESTAMP WHERE id = 1
            """);
    }

    public void markFailed(UUID id, String safeFailure) {
        jdbc.update("""
            UPDATE knowledge_documents SET status = 'FAILED', failure_message = ?,
                updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status <> 'READY'
            """, safeFailure, id);
    }

    private String metadata(Document document, int index, String content) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "documentId", document.id(),
                "title", document.title(),
                "source", document.source(),
                "sectionTitle", sectionTitle(content, document.title()),
                "chunkIndex", index));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize chunk metadata", exception);
        }
    }

    private String sectionTitle(String content, String fallback) {
        String firstLine = content.lines().findFirst().orElse("").strip();
        if (firstLine.startsWith("## ")) return firstLine.substring(3).strip();
        if (firstLine.startsWith("# ")) return firstLine.substring(2).strip();
        return fallback;
    }

    static String vectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder(vector.length * 8).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) value.append(',');
            value.append(Float.toString(vector[index]));
        }
        return value.append(']').toString();
    }

    public record Document(UUID id, String title, String source, String content, String status) { }
}
