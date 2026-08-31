package com.hammerly.ai.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration(proxyBeanMethods = false)
public class AiConfig {
    @Bean
    HammerlySystemPrompt hammerlySystemPrompt(
            @Value("${hammerly.ai.system-prompt}") Resource promptResource) throws IOException {
        return new HammerlySystemPrompt(promptResource.getContentAsString(StandardCharsets.UTF_8));
    }

    @Bean(destroyMethod = "close")
    ExecutorService contextExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
