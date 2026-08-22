package com.hammerly.worker.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hammerly.worker.idempotency.IdempotencyStore;
import com.hammerly.worker.idempotency.ProcessingClaim;
import com.hammerly.worker.observability.WorkerMetrics;
import com.hammerly.worker.summary.ConversationSummaryHandler;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.group-id=hammerly-worker-integration",
    "HAMMERLY_WORKER_CONCURRENCY=2",
    "hammerly.worker.events-topic=test.ai.events.v1",
    "hammerly.worker.jobs-topic=test.ai.jobs.v1"
})
@EmbeddedKafka(kraft = true, partitions = 3, topics = {
    "test.ai.events.v1", "test.ai.jobs.v1",
    "test.ai.events.v1.DLT", "test.ai.jobs.v1.DLT"
})
@SuppressWarnings("removal")
class KafkaConsumerIntegrationTest {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private WorkerMetrics metrics;

    @MockBean
    private IdempotencyStore idempotencyStore;

    @MockBean
    private ConversationSummaryHandler summaryHandler;

    private Consumer<String, String> dltConsumer;

    @AfterEach
    void closeConsumer() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
    }

    @Test
    void consumesEventsForDifferentConversations() throws Exception {
        when(idempotencyStore.claim(any())).thenAnswer(invocation -> {
            UUID eventId = invocation.getArgument(0);
            return new ProcessingClaim(eventId, ProcessingClaim.Status.ACQUIRED, "test-lock");
        });
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        send("test.ai.events.v1", "conversation-a", validMessage(first, "conversation-a"));
        send("test.ai.events.v1", "conversation-b", validMessage(second, "conversation-b"));

        verify(idempotencyStore, timeout(10_000)).complete(
            eq(new ProcessingClaim(first, ProcessingClaim.Status.ACQUIRED, "test-lock")));
        verify(idempotencyStore, timeout(10_000)).complete(
            eq(new ProcessingClaim(second, ProcessingClaim.Status.ACQUIRED, "test-lock")));
    }

    @Test
    void poisonEventRetriesThreeTimesThenReachesTopicDlt() throws Exception {
        dltConsumer = dltConsumer();
        broker.consumeFromAnEmbeddedTopic(dltConsumer, "test.ai.jobs.v1.DLT");

        send("test.ai.jobs.v1", "conversation-poison", "not-json");

        ConsumerRecord<String, String> dlt = KafkaTestUtils.getSingleRecord(
            dltConsumer, "test.ai.jobs.v1.DLT", Duration.ofSeconds(15));
        assertEquals("conversation-poison", dlt.key());
        assertEquals("not-json", dlt.value());
        assertEquals(1.0, metrics.dltCount("unknown"));
        assertEquals(3.0, metrics.retryCount("unknown"));
    }

    private void send(String topic, String key, String value) throws Exception {
        SendResult<String, String> ignored = kafkaTemplate.send(topic, key, value)
            .get(10, TimeUnit.SECONDS);
    }

    private Consumer<String, String> dltConsumer() {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
            "hammerly-worker-dlt-test-" + UUID.randomUUID(), "true", broker);
        return new DefaultKafkaConsumerFactory<>(properties,
            new StringDeserializer(), new StringDeserializer()).createConsumer();
    }

    private String validMessage(UUID eventId, String conversationId) {
        return """
            {
              "eventId":"%s",
              "eventType":"message.created",
              "eventVersion":1,
              "occurredAt":"2026-08-22T12:00:00Z",
              "producer":"hammerly-ai",
              "correlationId":"%s",
              "userId":"42",
              "conversationId":"%s",
              "payload":{"role":"ASSISTANT","content":"Answer","createdAt":"2026-08-22T12:00:00Z"}
            }
            """.formatted(eventId, UUID.randomUUID(), conversationId);
    }
}
