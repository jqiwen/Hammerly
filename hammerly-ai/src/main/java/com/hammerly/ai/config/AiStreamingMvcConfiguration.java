package com.hammerly.ai.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AiStreamingMvcConfiguration implements WebMvcConfigurer {
    private final AsyncTaskExecutor executor;
    private final Duration streamTimeout;

    public AiStreamingMvcConfiguration(
            AsyncTaskExecutor aiMvcStreamingExecutor,
            @Value("${hammerly.ai.stream-timeout}") Duration streamTimeout) {
        this.executor = aiMvcStreamingExecutor;
        this.streamTimeout = streamTimeout;
    }

    @Bean(destroyMethod = "close")
    static AsyncTaskExecutor aiMvcStreamingExecutor(
            @Value("${hammerly.ai.stream-max-concurrent}") int maxConcurrent) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("ai-sse-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(maxConcurrent);
        executor.setRejectTasksWhenLimitReached(true);
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(streamTimeout.toMillis());
    }
}
