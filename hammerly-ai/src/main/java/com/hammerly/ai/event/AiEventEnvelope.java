package com.hammerly.ai.event;

import java.time.Instant;
import java.util.UUID;

public record AiEventEnvelope<T>(
    UUID eventId,
    String eventType,
    int eventVersion,
    Instant occurredAt,
    String producer,
    UUID correlationId,
    String userId,
    String conversationId,
    T payload
) {
}
