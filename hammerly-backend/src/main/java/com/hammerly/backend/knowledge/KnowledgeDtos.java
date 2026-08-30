package com.hammerly.backend.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class KnowledgeDtos {
    private KnowledgeDtos() { }

    public record CreateDocumentRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 500) String source,
        @NotBlank @Size(max = 1_000_000) String content
    ) { }

    public record DocumentResponse(
        UUID id,
        String title,
        String source,
        String status,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt
    ) { }
}
