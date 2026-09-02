package com.hammerly.ai.config;

import com.hammerly.ai.event.AiEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KafkaEventProperties.class)
public class KafkaEventConfiguration {
    @Bean
    @ConditionalOnMissingBean(AiEventPublisher.class)
    AiEventPublisher noOpAiEventPublisher() {
        return turn -> { };
    }
}
