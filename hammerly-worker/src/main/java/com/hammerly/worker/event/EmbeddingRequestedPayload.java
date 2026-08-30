package com.hammerly.worker.event;

import java.util.UUID;

public record EmbeddingRequestedPayload(UUID documentId) {
}
