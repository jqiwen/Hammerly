package com.hammerly.worker.consumer;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

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
        KafkaEventListener listener = new KafkaEventListener(processor,
            new WorkerMetrics(new SimpleMeterRegistry()));
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "events", 0, 4L, "conversation-a", "{\"eventType\":\"message.created\"}");

        listener.consume(record, acknowledgment);

        InOrder order = inOrder(processor, acknowledgment);
        order.verify(processor).process(record.key(), record.value());
        order.verify(acknowledgment).acknowledge();
    }
}
