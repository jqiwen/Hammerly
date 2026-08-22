package com.hammerly.worker.consumer;

import com.hammerly.worker.observability.WorkerMetrics;
import com.hammerly.worker.processing.EventProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventListener {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventListener.class);

    private final EventProcessor processor;
    private final WorkerMetrics metrics;

    public KafkaEventListener(EventProcessor processor, WorkerMetrics metrics) {
        this.processor = processor;
        this.metrics = metrics;
    }

    @KafkaListener(
        topics = {"${hammerly.worker.events-topic}", "${hammerly.worker.jobs-topic}"},
        concurrency = "${HAMMERLY_WORKER_CONCURRENCY:3}"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String eventType = EventTypeExtractor.fromJson(record.value());
        try {
            processor.process(record.key(), record.value());
            acknowledgment.acknowledge();
        } catch (RuntimeException exception) {
            metrics.failed(eventType);
            log.warn("Worker event processing failed eventType={} topic={} partition={} offset={} errorType={}",
                eventType, record.topic(), record.partition(), record.offset(), rootCauseName(exception));
            throw exception;
        }
    }

    private String rootCauseName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }
}
