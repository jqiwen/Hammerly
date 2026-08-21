package com.hammerly.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AiStreamingMvcConfig implements WebMvcConfigurer {
    private final AiPlatformProperties properties;
    private final AsyncTaskExecutor aiStreamingExecutor;

    public AiStreamingMvcConfig(AiPlatformProperties properties, AsyncTaskExecutor aiStreamingExecutor) {
        this.properties = properties;
        this.aiStreamingExecutor = aiStreamingExecutor;
    }

    @Bean
    static AsyncTaskExecutor aiStreamingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-stream-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(aiStreamingExecutor);
        configurer.setDefaultTimeout(properties.streamTimeout().toMillis());
    }
}
