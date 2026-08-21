package com.hammerly.backend.client;

import com.hammerly.backend.dto.AiChatRequest;
import com.hammerly.backend.dto.AiChatResponse;
import com.hammerly.backend.dto.AiServiceStatus;
import com.hammerly.backend.exception.AiServiceUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiPlatformClient {
    private static final String UNAVAILABLE_MESSAGE = "Hammerly AI service is unavailable.";
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

    public AiChatResponse chat(AiChatRequest request) {
        try {
            AiChatResponse response = restClient.post()
                .uri("/internal/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AiChatResponse.class);
            if (response == null || response.answer() == null || response.answer().isBlank()) {
                throw new AiServiceUnavailableException("Hammerly AI returned an empty response.");
            }
            return response;
        } catch (AiServiceUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("Hammerly AI chat call failed ({})", rootCauseName(exception));
            throw new AiServiceUnavailableException(UNAVAILABLE_MESSAGE, exception);
        }
    }

    public void stream(AiChatRequest request, OutputStream browserOutput) {
        try {
            restClient.post()
                .uri("/internal/ai/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(request)
                .exchange((clientRequest, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new AiServiceUnavailableException(UNAVAILABLE_MESSAGE);
                    }
                    copyAndFlush(response.getBody(), browserOutput);
                    return null;
                });
        } catch (AiServiceUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("Hammerly AI stream proxy failed ({})", rootCauseName(exception));
            throw new AiServiceUnavailableException(UNAVAILABLE_MESSAGE, exception);
        }
    }

    private void copyAndFlush(InputStream aiInput, OutputStream browserOutput) throws IOException {
        byte[] buffer = new byte[1_024];
        int bytesRead;
        while ((bytesRead = aiInput.read(buffer)) != -1) {
            browserOutput.write(buffer, 0, bytesRead);
            browserOutput.flush();
        }
    }

    private String rootCauseName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }
}
