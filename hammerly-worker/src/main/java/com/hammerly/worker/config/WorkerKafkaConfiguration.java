package com.hammerly.worker.config;

import com.hammerly.worker.consumer.EventTypeExtractor;
import com.hammerly.worker.observability.WorkerMetrics;
import java.time.Clock;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerKafkaConfiguration {
    private static final Logger log = LoggerFactory.getLogger(WorkerKafkaConfiguration.class);

    @Bean
    Clock workerClock() {
        return Clock.systemUTC();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                          WorkerMetrics metrics) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate, (record, exception) -> {
                String eventType = EventTypeExtractor.fromJson((String) record.value());
                metrics.dlt(eventType);
                log.error("Worker event sent to DLT eventType={} topic={} partition={} offset={} errorType={}",
                    eventType, record.topic(), record.partition(), record.offset(),
                    rootCauseName(exception));
                return new TopicPartition(record.topic() + ".DLT", record.partition());
            });
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            recoverer, new FixedBackOff(500L, 3L));
        errorHandler.setCommitRecovered(true);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) -> {
            if (deliveryAttempt > 1) {
                metrics.retry(EventTypeExtractor.fromJson((String) record.value()));
            }
        });
        return errorHandler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    private static String rootCauseName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }
}
