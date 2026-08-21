package com.hammerly.backend.client;

import com.hammerly.backend.dto.AiServiceStatus;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiPlatformClient {
    private static final Logger log = LoggerFactory.getLogger(AiPlatformClient.class);

    private final RestClient restClient;

    public AiPlatformClient(@Qualifier("aiPlatformRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<AiServiceStatus> status() {
        try {
            AiServiceStatus response = restClient.get()
                .uri("/internal/ai/status")
                .retrieve()
                .body(AiServiceStatus.class);
            return Optional.ofNullable(response);
        } catch (RestClientException exception) {
            log.warn("Hammerly AI status check failed ({})", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
