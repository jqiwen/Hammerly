package com.hammerly.ai.config;

import com.hammerly.ai.event.AiEventPublisher;
import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(KafkaEventProperties.class)
public class KafkaEventConfiguration {
    @Bean(name = "kafkaEventExecutor")
    Executor kafkaEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("kafka-event-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1_000);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean(AiEventPublisher.class)
    AiEventPublisher noOpAiEventPublisher() {
        return turn -> { };
    }
}
