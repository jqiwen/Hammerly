package com.hammerly.ai.event;

import com.hammerly.ai.config.KafkaEventProperties;
import com.hammerly.ai.dto.ChatRole;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.redis.RedisStateClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "hammerly.kafka", name = "enabled", havingValue = "true")
public class KafkaAiEventPublisher implements AiEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaAiEventPublisher.class);
    private static final String PRODUCER = "hammerly-ai";
    private static final int EVENT_VERSION = 1;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaEventProperties properties;
    private final RedisStateClient redis;
    private final AiMetrics metrics;

    public KafkaAiEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                 KafkaEventProperties properties,
                                 RedisStateClient redis,
                                 AiMetrics metrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.redis = redis;
        this.metrics = metrics;
    }

    @Override
    public void publishSuccessfulTurn(SuccessfulAiTurn turn) {
        try {
            UUID correlationId = UUID.randomUUID();
            List<PendingPublish> pending = new ArrayList<>(2);
            sendForBrokerAck(properties.eventsTopic(), turn.conversationId(), new AiEventEnvelope<>(
                UUID.randomUUID(), "message.created", EVENT_VERSION, turn.occurredAt(), PRODUCER,
                correlationId, turn.userId(), turn.conversationId(),
                new MessageCreatedPayload(ChatRole.USER, turn.question(), turn.occurredAt())), pending);
            sendForBrokerAck(properties.eventsTopic(), turn.conversationId(), new AiEventEnvelope<>(
                UUID.randomUUID(), "message.created", EVENT_VERSION, turn.occurredAt(), PRODUCER,
                correlationId, turn.userId(), turn.conversationId(),
                new MessageCreatedPayload(ChatRole.ASSISTANT, turn.answer(), turn.occurredAt())), pending);
            awaitBrokerAcknowledgements(pending);
            dispatchSummaryRequest(turn, correlationId);
        } catch (RuntimeException exception) {
            metrics.kafkaPublishFailure("successful_turn");
            log.warn("Kafka turn publication failed open eventType=successful_turn conversation={} errorType={}",
                turn.conversationId(), rootCauseName(exception));
        }
    }

    private void sendForBrokerAck(String topic, String key, AiEventEnvelope<?> event,
                                  List<PendingPublish> pending) {
        try {
            pending.add(new PendingPublish(event, kafkaTemplate.send(topic, key, event)));
        } catch (RuntimeException exception) {
            recordPublishFailure(event, exception);
        }
    }

    private void awaitBrokerAcknowledgements(List<PendingPublish> pending) {
        long deadline = System.nanoTime() + properties.brokerAckTimeout().toNanos();
        for (PendingPublish publication : pending) {
            try {
                long remaining = Math.max(0, deadline - System.nanoTime());
                publication.future().get(remaining, TimeUnit.NANOSECONDS);
                metrics.kafkaPublishSuccess(publication.event().eventType());
            } catch (TimeoutException | ExecutionException exception) {
                recordPublishFailure(publication.event(), exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                recordPublishFailure(publication.event(), exception);
                return;
            } catch (RuntimeException exception) {
                recordPublishFailure(publication.event(), exception);
            }
        }
    }

    private void dispatchSummaryRequest(SuccessfulAiTurn turn, UUID correlationId) {
        if (turn.storedMessageCount() < properties.summaryAfterMessages()) {
            return;
        }
        if (claimSummaryRequest(turn)) {
            List<PendingPublish> pending = new ArrayList<>(1);
            sendForBrokerAck(properties.jobsTopic(), turn.conversationId(), new AiEventEnvelope<>(
                UUID.randomUUID(), "conversation.summary.requested", EVENT_VERSION,
                turn.occurredAt(), PRODUCER, correlationId, turn.userId(), turn.conversationId(),
                new ConversationSummaryRequestedPayload(
                    turn.storedMessageCount(), turn.conversationSnapshot())), pending);
            awaitBrokerAcknowledgements(pending);
        }
    }

    private boolean claimSummaryRequest(SuccessfulAiTurn turn) {
        String marker = "hammerly:ai:summary-requested:" + turn.userId() + ":"
            + turn.conversationId() + ":" + properties.summaryAfterMessages();
        try {
            return redis.setIfAbsent(marker, turn.occurredAt().toString(),
                properties.summaryRequestMarkerTtl());
        } catch (RuntimeException exception) {
            metrics.kafkaPublishFailure("conversation.summary.requested");
            log.warn("Kafka summary request skipped because marker write failed conversation={} errorType={}",
                turn.conversationId(), rootCauseName(exception));
            return false;
        }
    }

    private void recordPublishFailure(AiEventEnvelope<?> event, Throwable failure) {
        metrics.kafkaPublishFailure(event.eventType());
        log.warn("Kafka event publish failed eventId={} eventType={} conversation={} errorType={}",
            event.eventId(), event.eventType(), event.conversationId(), rootCauseName(failure));
    }

    private String rootCauseName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    private record PendingPublish(AiEventEnvelope<?> event, CompletableFuture<?> future) {
    }
}
