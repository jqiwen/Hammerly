package com.hammerly.worker.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.worker.event.ConversationSummaryRequestedPayload;
import com.hammerly.worker.event.EventEnvelope;
import com.hammerly.worker.event.SummaryMessage;
import com.hammerly.worker.observability.WorkerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConversationSummaryHandlerTest {
    @Test
    void generatesAndStoresSummarySeparately() {
        ConversationSummarizer summarizer = mock(ConversationSummarizer.class);
        ConversationSummaryRepository repository = mock(ConversationSummaryRepository.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneOffset.UTC);
        ConversationSummaryHandler handler = new ConversationSummaryHandler(summarizer, repository,
            new WorkerMetrics(registry), clock);
        ConversationSummaryRequestedPayload payload = new ConversationSummaryRequestedPayload(10,
            List.of(new SummaryMessage("USER", "How do I bid?", clock.instant())));
        when(summarizer.summarize(payload)).thenReturn("User needs bidding instructions.");
        UUID eventId = UUID.randomUUID();
        EventEnvelope event = event(eventId, payload);

        handler.handle(event, payload);

        ArgumentCaptor<ConversationSummary> summary = ArgumentCaptor.forClass(ConversationSummary.class);
        verify(repository).save(summary.capture());
        assertEquals("User needs bidding instructions.", summary.getValue().summary());
        assertEquals(eventId, summary.getValue().sourceEventId());
        assertEquals(1.0, registry.get("hammerly.worker.summary.success").counter().count());
        assertEquals("hammerly:conversation:summary:42:conversation-a",
            RedisConversationSummaryRepository.key("42", "conversation-a"));
    }

    private EventEnvelope event(UUID eventId, ConversationSummaryRequestedPayload payload) {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return new EventEnvelope(eventId, "conversation.summary.requested", 1,
            Instant.parse("2026-08-22T12:00:00Z"), "hammerly-ai", UUID.randomUUID(),
            "42", "conversation-a", mapper.valueToTree(payload));
    }
}
