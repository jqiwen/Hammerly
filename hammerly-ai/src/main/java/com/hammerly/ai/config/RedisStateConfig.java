package com.hammerly.ai.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiStateProperties.class)
public class RedisStateConfig {
    @Bean
    Clock aiClock() {
        return Clock.systemUTC();
    }
}
