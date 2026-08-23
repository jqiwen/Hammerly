package com.hammerly.backend.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiPlatformProperties.class)
public class AiPlatformClientConfig {
    @Bean(destroyMethod = "close")
    CloseableHttpClient aiPlatformHttpClient(AiPlatformProperties properties) {
        PoolingHttpClientConnectionManager connectionManager =
            PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(properties.connectionPoolMaxTotal())
                .setMaxConnPerRoute(properties.connectionPoolMaxPerRoute())
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.of(properties.connectTimeout()))
            .setConnectionRequestTimeout(Timeout.of(properties.connectTimeout()))
            .setResponseTimeout(Timeout.of(properties.readTimeout()))
            .build();
        return HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .evictExpiredConnections()
            .evictIdleConnections(Timeout.ofMinutes(1))
            .disableAutomaticRetries()
            .build();
    }

    @Bean
    RestClient aiPlatformRestClient(RestClient.Builder builder,
                                    AiPlatformProperties properties,
                                    CloseableHttpClient aiPlatformHttpClient) {
        HttpComponentsClientHttpRequestFactory requestFactory =
            new HttpComponentsClientHttpRequestFactory(aiPlatformHttpClient);

        RestClient.Builder configuredBuilder = builder
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory);

        if (StringUtils.hasText(properties.internalToken())) {
            configuredBuilder.requestInterceptor(new InternalAiTokenInterceptor(properties.internalToken()));
        }

        return configuredBuilder.build();
    }
}
