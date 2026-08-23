package com.hammerly.worker.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.worker.idempotency.IdempotencyStore;
import com.hammerly.worker.idempotency.ProcessingClaim;
import com.hammerly.worker.observability.WorkerMetrics;
import com.hammerly.worker.summary.ConversationSummaryHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventProcessorTest {
    private static final UUID EVENT_ID = UUID.fromString("b29bd72b-a2d5-4938-90f0-151867ac4c7a");
    private IdempotencyStore idempotency;
    private ConversationSummaryHandler summaryHandler;
    private SimpleMeterRegistry registry;
    private EventProcessor processor;

    @BeforeEach
    void setUp() {
        idempotency = mock(IdempotencyStore.class);
        summaryHandler = mock(ConversationSummaryHandler.class);
        registry = new SimpleMeterRegistry();
        processor = new EventProcessor(new ObjectMapper().findAndRegisterModules(), idempotency,
            summaryHandler, new WorkerMetrics(registry));
    }

    @Test
    void successfulMessageIsProcessedAndMarkedComplete() {
        ProcessingClaim claim = new ProcessingClaim(EVENT_ID,
            ProcessingClaim.Status.ACQUIRED, "token");
        when(idempotency.claim(EVENT_ID)).thenReturn(claim);

        processor.process("conversation-a", messageJson());

        verify(idempotency).complete(claim);
        assertEquals(1.0, registry.get("hammerly.worker.analytics.ai_turn.completed")
            .counter().count());
        assertEquals(1.0, registry.get("hammerly.worker.event.processed")
            .tag("event_type", "message.created").counter().count());
    }

    @Test
    void duplicateEventDoesNotRepeatSideEffect() {
        when(idempotency.claim(EVENT_ID)).thenReturn(new ProcessingClaim(EVENT_ID,
            ProcessingClaim.Status.ALREADY_PROCESSED, null));

        processor.process("conversation-a", messageJson());

        verify(idempotency, never()).complete(any());
        assertEquals(1.0, registry.get("hammerly.worker.event.duplicate")
            .tag("event_type", "message.created").counter().count());
    }

    @Test
    void wrongPartitionKeyIsRejectedBeforeClaiming() {
        assertThrows(IllegalArgumentException.class,
            () -> processor.process("another-conversation", messageJson()));
        verify(idempotency, never()).claim(any());
    }

    private String messageJson() {
        return """
            {
              "eventId":"b29bd72b-a2d5-4938-90f0-151867ac4c7a",
              "eventType":"message.created",
              "eventVersion":1,
              "occurredAt":"2026-08-22T12:00:00Z",
              "producer":"hammerly-ai",
              "correlationId":"e88b1870-46f2-4974-bd23-d790accabea5",
              "userId":"42",
              "conversationId":"conversation-a",
              "payload":{"role":"ASSISTANT","content":"Answer","createdAt":"2026-08-22T12:00:00Z"}
            }
            """;
    }
}
