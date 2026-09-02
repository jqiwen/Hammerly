package com.hammerly.ai.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.ai.config.KafkaEventProperties;
import com.hammerly.ai.dto.ChatRole;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.redis.RedisStateClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaAiEventPublisherTest {
    @Test
    @SuppressWarnings("unchecked")
    void publishesTypedVersionedEventsWithConversationKey() throws Exception {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));
        RedisStateClient redis = mock(RedisStateClient.class);
        when(redis.setIfAbsent(any(), any(), any())).thenReturn(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaAiEventPublisher publisher = new KafkaAiEventPublisher(kafkaTemplate,
            properties(), redis, new AiMetrics(registry), Runnable::run);
        Instant now = Instant.parse("2026-08-22T12:00:00Z");

        publisher.publishSuccessfulTurn(new SuccessfulAiTurn("42", "conversation-a",
            "Question", "Answer", now, 10, List.of(
                new SummaryMessage(ChatRole.USER, "Question", now),
                new SummaryMessage(ChatRole.ASSISTANT, "Answer", now))));

        ArgumentCaptor<AiEventEnvelope<?>> events = ArgumentCaptor.forClass(AiEventEnvelope.class);
        verify(kafkaTemplate, times(2)).send(eq("hammerly.ai.events.v1"),
            eq("conversation-a"), events.capture());
        verify(kafkaTemplate).send(eq("hammerly.ai.jobs.v1"),
            eq("conversation-a"), events.capture());
        assertTrue(events.getAllValues().stream().allMatch(event -> event.eventId() != null));
        assertTrue(events.getAllValues().stream().allMatch(event -> event.eventVersion() == 1));
        assertTrue(events.getAllValues().stream()
            .anyMatch(event -> event.eventType().equals("conversation.summary.requested")));
        assertEquals(2.0, registry.get("hammerly.kafka.publish.success")
            .tag("eventType", "message.created").counter().count());

        String json = new ObjectMapper().findAndRegisterModules()
            .writeValueAsString(events.getAllValues().getFirst());
        assertTrue(json.contains("\"eventVersion\":1"));
        assertTrue(json.contains("\"eventId\":"));
        assertNotNull(events.getAllValues().getFirst().correlationId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendFailureIsRecordedAndDoesNotEscape() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(), any(), any()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaAiEventPublisher publisher = new KafkaAiEventPublisher(kafkaTemplate,
            properties(), mock(RedisStateClient.class), new AiMetrics(registry), Runnable::run);
        Instant now = Instant.parse("2026-08-22T12:00:00Z");

        publisher.publishSuccessfulTurn(new SuccessfulAiTurn("42", "conversation-a",
            "Question", "Answer", now, 2, List.of()));

        assertEquals(2.0, registry.get("hammerly.kafka.publish.failure").counter().count());
    }

    @Test
    @SuppressWarnings("unchecked")
    void brokerAckTimeoutIsBoundedFailOpenAndBothMessagesAreAttempted() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(), any(), any()))
            .thenReturn(new CompletableFuture<>());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaAiEventPublisher publisher = new KafkaAiEventPublisher(kafkaTemplate,
            properties(Duration.ofMillis(20)), mock(RedisStateClient.class),
            new AiMetrics(registry), Runnable::run);
        Instant now = Instant.parse("2026-08-22T12:00:00Z");

        assertTimeoutPreemptively(Duration.ofMillis(250), () ->
            publisher.publishSuccessfulTurn(new SuccessfulAiTurn("42", "conversation-a",
                "Question", "Answer", now, 2, List.of())));

        verify(kafkaTemplate, times(2)).send(eq("hammerly.ai.events.v1"),
            eq("conversation-a"), any());
        assertEquals(2.0, registry.get("hammerly.kafka.publish.failure")
            .tag("eventType", "message.created").counter().count());
    }

    @Test
    @SuppressWarnings("unchecked")
    void downstreamSummaryWorkRemainsAsynchronous() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));
        RedisStateClient redis = mock(RedisStateClient.class);
        when(redis.setIfAbsent(any(), any(), any())).thenReturn(true);
        AtomicReference<Runnable> queuedSummary = new AtomicReference<>();
        KafkaAiEventPublisher publisher = new KafkaAiEventPublisher(kafkaTemplate,
            properties(), redis, new AiMetrics(new SimpleMeterRegistry()), queuedSummary::set);
        Instant now = Instant.parse("2026-08-22T12:00:00Z");

        publisher.publishSuccessfulTurn(new SuccessfulAiTurn("42", "conversation-a",
            "Question", "Answer", now, 10, List.of()));

        verify(kafkaTemplate, times(2)).send(eq("hammerly.ai.events.v1"),
            eq("conversation-a"), any());
        verify(kafkaTemplate, never()).send(eq("hammerly.ai.jobs.v1"), any(), any());
        assertNotNull(queuedSummary.get());

        queuedSummary.get().run();
        verify(kafkaTemplate).send(eq("hammerly.ai.jobs.v1"), eq("conversation-a"), any());
    }

    private KafkaEventProperties properties() {
        return properties(Duration.ofMillis(400));
    }

    private KafkaEventProperties properties(Duration brokerAckTimeout) {
        return new KafkaEventProperties(true, "hammerly.ai.events.v1", "hammerly.ai.jobs.v1",
            10, Duration.ofDays(7), brokerAckTimeout);
    }
}
