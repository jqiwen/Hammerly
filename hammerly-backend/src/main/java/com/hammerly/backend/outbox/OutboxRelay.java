package com.hammerly.backend.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "hammerly.kafka", name = "enabled", havingValue = "true",
    matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry metrics;
    private final int batchSize;
    private final Duration retryDelay;
    private final Duration publishTimeout;

    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                       MeterRegistry metrics,
                       @Value("${hammerly.outbox.batch-size}") int batchSize,
                       @Value("${hammerly.outbox.retry-delay}") Duration retryDelay,
                       @Value("${hammerly.outbox.publish-timeout}") Duration publishTimeout) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.metrics = metrics;
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
        this.publishTimeout = publishTimeout;
    }

    @Scheduled(fixedDelayString = "${hammerly.outbox.relay-delay:2s}")
    @Transactional
    public void publishPending() {
        List<OutboxRow> rows = jdbc.query("""
            SELECT id, aggregate_id, topic, event_type, payload::text
            FROM outbox_events
            WHERE published_at IS NULL
              AND next_attempt_at <= CURRENT_TIMESTAMP
              AND (status IN ('PENDING', 'RETRY')
                   OR (status = 'PROCESSING' AND next_attempt_at < CURRENT_TIMESTAMP - INTERVAL '1 minute'))
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT ?
            """, (rs, row) -> new OutboxRow(
                rs.getObject("id", UUID.class), rs.getObject("aggregate_id", UUID.class),
                rs.getString("topic"), rs.getString("event_type"), rs.getString("payload")), batchSize);

        for (OutboxRow row : rows) {
            jdbc.update("UPDATE outbox_events SET status = 'PROCESSING' WHERE id = ?", row.id());
            try {
                kafka.send(row.topic(), row.aggregateId().toString(), row.payload())
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
                jdbc.update("""
                    UPDATE outbox_events SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP,
                        last_error = NULL WHERE id = ?
                    """, row.id());
                metrics.counter("hammerly.outbox.published", "event_type", row.eventType()).increment();
            } catch (Exception exception) {
                String error = exception.getClass().getSimpleName();
                jdbc.update("""
                    UPDATE outbox_events SET status = 'RETRY', retry_count = retry_count + 1,
                        next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'),
                        last_error = ? WHERE id = ?
                    """, retryDelay.toMillis(), error, row.id());
                metrics.counter("hammerly.outbox.publish.failure", "event_type", row.eventType()).increment();
                log.warn("Outbox publication retained for retry eventId={} eventType={} errorType={}",
                    row.id(), row.eventType(), error);
            }
        }
    }

    private record OutboxRow(UUID id, UUID aggregateId, String topic,
                             String eventType, String payload) { }
}
