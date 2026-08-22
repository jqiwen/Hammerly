package com.hammerly.worker.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
    UUID eventId,
    String eventType,
    int eventVersion,
    Instant occurredAt,
    String producer,
    UUID correlationId,
    String userId,
    String conversationId,
    JsonNode payload
) {
}
