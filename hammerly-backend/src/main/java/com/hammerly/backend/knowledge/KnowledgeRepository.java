package com.hammerly.backend.knowledge;

import com.hammerly.backend.knowledge.KnowledgeDtos.DocumentResponse;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeRepository {
    private final JdbcTemplate jdbc;

    public KnowledgeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DocumentResponse> findBySourceAndHash(String source, String hash) {
        return jdbc.query("""
            SELECT id, title, source, status, failure_message, created_at, updated_at
            FROM knowledge_documents WHERE source = ? AND content_hash = ?
            """, this::map, source, hash).stream().findFirst();
    }

    public Optional<DocumentResponse> findById(UUID id) {
        return jdbc.query("""
            SELECT id, title, source, status, failure_message, created_at, updated_at
            FROM knowledge_documents WHERE id = ?
            """, this::map, id).stream().findFirst();
    }

    public void insertDocument(UUID id, String title, String source, String content, String hash) {
        jdbc.update("""
            INSERT INTO knowledge_documents
                (id, title, source, content, content_hash, status)
            VALUES (?, ?, ?, ?, ?, 'PENDING')
            """, id, title, source, content, hash);
    }

    public void insertOutbox(UUID eventId, UUID documentId, String topic, String payload) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO outbox_events
                    (id, event_type, event_version, aggregate_id, topic, payload)
                VALUES (?, 'embedding.requested', 1, ?, ?, CAST(? AS jsonb))
                """);
            statement.setObject(1, eventId);
            statement.setObject(2, documentId);
            statement.setString(3, topic);
            statement.setString(4, payload);
            return statement;
        });
    }

    private DocumentResponse map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new DocumentResponse(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("source"),
            rs.getString("status"),
            rs.getString("failure_message"),
            rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant());
    }
}
