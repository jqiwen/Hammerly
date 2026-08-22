package com.hammerly.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiPlatformProperties.class)
public class AiPlatformClientConfig {
    @Bean
    RestClient aiPlatformRestClient(RestClient.Builder builder, AiPlatformProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient.Builder configuredBuilder = builder
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory);

        if (StringUtils.hasText(properties.internalToken())) {
            configuredBuilder.requestInterceptor(new InternalAiTokenInterceptor(properties.internalToken()));
        }

        return configuredBuilder.build();
    }
}
