package com.hammerly.ai.config;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.core.Timeout;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.Map;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.openaisdk.autoconfigure.OpenAiSdkAutoConfigurationUtil;
import org.springframework.ai.model.openaisdk.autoconfigure.OpenAiSdkChatProperties;
import org.springframework.ai.model.openaisdk.autoconfigure.OpenAiSdkConnectionProperties;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.openaisdk.AbstractOpenAiSdkOptions;
import org.springframework.ai.openaisdk.OpenAiSdkChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Builds the existing Spring AI model with fine-grained OpenAI transport timeouts.
 * The SDK's default Duration property is a total-call timeout, which is unsafe for
 * valid long SSE responses; a fine-grained Timeout leaves total duration unbounded.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OpenAiSdkConnectionProperties.class, OpenAiSdkChatProperties.class})
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai-sdk", matchIfMissing = true)
public class OpenAiTransportConfiguration {
    @Bean
    @ConditionalOnMissingBean(OpenAiSdkChatModel.class)
    OpenAiSdkChatModel hammerlyOpenAiChatModel(
            OpenAiSdkConnectionProperties commonProperties,
            OpenAiSdkChatProperties chatProperties,
            LlmResilienceProperties resilienceProperties,
            ToolCallingManager toolCallingManager,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatModelObservationConvention> observationConvention,
            ObjectProvider<ToolExecutionEligibilityPredicate> eligibilityPredicate) {
        AbstractOpenAiSdkOptions resolved = OpenAiSdkAutoConfigurationUtil
            .resolveConnectionProperties(commonProperties, chatProperties);
        Timeout timeout = Timeout.builder()
            .connect(resilienceProperties.connectTimeout())
            .read(resilienceProperties.idleTimeout())
            .write(resilienceProperties.connectTimeout())
            .request(Duration.ZERO)
            .build();

        OpenAIClient syncClient = configure(OpenAIOkHttpClient.builder(), resolved, timeout).build();
        OpenAIClientAsync asyncClient = configure(
            OpenAIOkHttpClientAsync.builder(), resolved, timeout).build();

        OpenAiSdkChatModel model = OpenAiSdkChatModel.builder()
            .openAiClient(syncClient)
            .openAiClientAsync(asyncClient)
            .options(chatProperties.getOptions())
            .toolCallingManager(toolCallingManager)
            .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
            .toolExecutionEligibilityPredicate(eligibilityPredicate.getIfUnique(
                DefaultToolExecutionEligibilityPredicate::new))
            .build();
        observationConvention.ifAvailable(model::setObservationConvention);
        return model;
    }

    private OpenAIOkHttpClient.Builder configure(OpenAIOkHttpClient.Builder builder,
                                                  AbstractOpenAiSdkOptions options,
                                                  Timeout timeout) {
        builder.baseUrl(options.getBaseUrl()).timeout(timeout).maxRetries(0);
        if (options.getCredential() != null) {
            builder.credential(options.getCredential());
        } else {
            builder.apiKey(options.getApiKey());
        }
        if (StringUtils.hasText(options.getOrganizationId())) {
            builder.organization(options.getOrganizationId());
        }
        if (options.getProxy() != null) {
            builder.proxy(options.getProxy());
        }
        addHeaders(builder, options.getCustomHeaders());
        return builder;
    }

    private OpenAIOkHttpClientAsync.Builder configure(OpenAIOkHttpClientAsync.Builder builder,
                                                       AbstractOpenAiSdkOptions options,
                                                       Timeout timeout) {
        builder.baseUrl(options.getBaseUrl()).timeout(timeout).maxRetries(0);
        if (options.getCredential() != null) {
            builder.credential(options.getCredential());
        } else {
            builder.apiKey(options.getApiKey());
        }
        if (StringUtils.hasText(options.getOrganizationId())) {
            builder.organization(options.getOrganizationId());
        }
        if (options.getProxy() != null) {
            builder.proxy(options.getProxy());
        }
        if (options.getCustomHeaders() != null) {
            builder.headers(options.getCustomHeaders().entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey,
                    entry -> java.util.List.of(entry.getValue()))));
        }
        return builder;
    }

    private void addHeaders(OpenAIOkHttpClient.Builder builder, Map<String, String> headers) {
        if (headers != null) {
            builder.headers(headers.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey,
                    entry -> java.util.List.of(entry.getValue()))));
        }
    }
}
