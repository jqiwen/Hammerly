package com.hammerly.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
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

    @Bean(destroyMethod = "close")
    static AsyncTaskExecutor aiStreamingExecutor(AiPlatformProperties properties) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("ai-stream-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(properties.streamMaxConcurrent());
        executor.setRejectTasksWhenLimitReached(true);
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(aiStreamingExecutor);
        configurer.setDefaultTimeout(properties.streamTimeout().toMillis());
    }
}
