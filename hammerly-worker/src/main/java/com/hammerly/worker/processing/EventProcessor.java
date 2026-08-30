package com.hammerly.worker.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.worker.event.ConversationSummaryRequestedPayload;
import com.hammerly.worker.event.EventEnvelope;
import com.hammerly.worker.event.MessageCreatedPayload;
import com.hammerly.worker.event.EmbeddingRequestedPayload;
import com.hammerly.worker.embedding.EmbeddingJobHandler;
import com.hammerly.worker.idempotency.IdempotencyStore;
import com.hammerly.worker.idempotency.ProcessingClaim;
import com.hammerly.worker.observability.WorkerMetrics;
import com.hammerly.worker.summary.ConversationSummaryHandler;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class EventProcessor {
    private final ObjectMapper objectMapper;
    private final IdempotencyStore idempotencyStore;
    private final ConversationSummaryHandler summaryHandler;
    private final WorkerMetrics metrics;
    private final EmbeddingJobHandler embeddingHandler;

    public EventProcessor(ObjectMapper objectMapper, IdempotencyStore idempotencyStore,
                          ConversationSummaryHandler summaryHandler, WorkerMetrics metrics,
                          EmbeddingJobHandler embeddingHandler) {
        this.objectMapper = objectMapper;
        this.idempotencyStore = idempotencyStore;
        this.summaryHandler = summaryHandler;
        this.metrics = metrics;
        this.embeddingHandler = embeddingHandler;
    }

    public void process(String partitionKey, String json) {
        EventEnvelope event = deserialize(json);
        validate(partitionKey, event);
        ProcessingClaim claim = idempotencyStore.claim(event.eventId());
        if (claim.status() == ProcessingClaim.Status.ALREADY_PROCESSED) {
            metrics.duplicate(event.eventType());
            return;
        }
        if (claim.status() == ProcessingClaim.Status.IN_PROGRESS) {
            throw new EventInProgressException("Event is already being processed: " + event.eventId());
        }

        try {
            dispatch(event);
            idempotencyStore.complete(claim);
            metrics.processed(event.eventType());
        } catch (RuntimeException exception) {
            try {
                idempotencyStore.release(claim);
            } catch (RuntimeException releaseFailure) {
                exception.addSuppressed(releaseFailure);
            }
            throw exception;
        }
    }

    private EventEnvelope deserialize(String json) {
        try {
            return objectMapper.readValue(json, EventEnvelope.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid event JSON", exception);
        }
    }

    private void validate(String partitionKey, EventEnvelope event) {
        if (event.eventId() == null || event.eventVersion() != 1
                || event.occurredAt() == null || event.correlationId() == null
                || isBlank(event.eventType()) || isBlank(event.producer())
                || event.payload() == null) {
            throw new IllegalArgumentException("Event envelope is incomplete or unsupported");
        }
        if ("embedding.requested".equals(event.eventType())) {
            if (event.aggregateId() == null || !Objects.equals(partitionKey, event.aggregateId().toString())) {
                throw new IllegalArgumentException("Knowledge event key must equal aggregateId");
            }
        } else if (isBlank(event.userId()) || isBlank(event.conversationId())
                || !Objects.equals(partitionKey, event.conversationId())) {
            throw new IllegalArgumentException("Conversation event key must equal conversationId");
        }
    }

    private void dispatch(EventEnvelope event) {
        switch (event.eventType()) {
            case "message.created" -> processMessage(event);
            case "conversation.summary.requested" -> summaryHandler.handle(event,
                convert(event, ConversationSummaryRequestedPayload.class));
            case "embedding.requested" -> embeddingHandler.handle(event,
                convert(event, EmbeddingRequestedPayload.class));
            case "conversation.completed" ->
                throw new UnsupportedEventTypeException(
                    "No Phase 4 handler is enabled for " + event.eventType());
            default -> throw new UnsupportedEventTypeException(
                "Unknown event type " + event.eventType());
        }
    }

    private void processMessage(EventEnvelope event) {
        MessageCreatedPayload payload = convert(event, MessageCreatedPayload.class);
        if (isBlank(payload.role()) || isBlank(payload.content()) || payload.createdAt() == null) {
            throw new IllegalArgumentException("message.created payload is incomplete");
        }
        if ("ASSISTANT".equalsIgnoreCase(payload.role())) {
            metrics.completedAiTurn();
        }
    }

    private <T> T convert(EventEnvelope event, Class<T> type) {
        try {
            return objectMapper.treeToValue(event.payload(), type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(event.eventType() + " payload is invalid", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
