package com.hammerly.worker.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hammerly.worker.observability.WorkerMetrics;
import com.hammerly.worker.processing.EventProcessor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.kafka.support.Acknowledgment;

class KafkaEventListenerTest {
    @Test
    void acknowledgesOnlyAfterProcessingReturnsSuccessfully() {
        EventProcessor processor = mock(EventProcessor.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaEventListener listener = new KafkaEventListener(processor,
            new WorkerMetrics(registry));
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "events", 0, 4L, "conversation-a", "{\"eventType\":\"message.created\"}");

        listener.consume(record, acknowledgment);

        InOrder order = inOrder(processor, acknowledgment);
        order.verify(processor).process(record.key(), record.value());
        order.verify(acknowledgment).acknowledge();
        assertEquals(1L, registry.get("kafka.processing.duration")
            .tag("event_type", "message.created")
            .tag("outcome", "success").timer().count());
    }

    @Test
    void recordsFailedProcessingAndDoesNotAcknowledge() {
        EventProcessor processor = mock(EventProcessor.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaEventListener listener = new KafkaEventListener(processor, new WorkerMetrics(registry));
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "events", 0, 4L, "conversation-a", "{\"eventType\":\"message.created\"}");
        doThrow(new IllegalStateException("processing failed"))
            .when(processor).process(record.key(), record.value());

        assertThrows(IllegalStateException.class, () -> listener.consume(record, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
        assertEquals(1L, registry.get("kafka.processing.duration")
            .tag("event_type", "message.created")
            .tag("outcome", "error").timer().count());
    }
}
