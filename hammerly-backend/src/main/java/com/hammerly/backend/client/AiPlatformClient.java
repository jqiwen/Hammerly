package com.hammerly.backend.client;

import com.hammerly.backend.dto.AiChatRequest;
import com.hammerly.backend.dto.AiChatResponse;
import com.hammerly.backend.dto.AiServiceStatus;
import com.hammerly.backend.exception.AiRateLimitExceededException;
import com.hammerly.backend.exception.AiServiceUnavailableException;
import com.hammerly.backend.observability.CoreAiMetrics;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiPlatformClient {
    private static final String UNAVAILABLE_MESSAGE = "Hammerly AI service is unavailable.";
    private static final Logger log = LoggerFactory.getLogger(AiPlatformClient.class);

    private final RestClient restClient;
    private final CoreAiMetrics metrics;

    public AiPlatformClient(@Qualifier("aiPlatformRestClient") RestClient restClient) {
        this(restClient, null);
    }

    @Autowired
    public AiPlatformClient(@Qualifier("aiPlatformRestClient") RestClient restClient,
                            CoreAiMetrics metrics) {
        this.restClient = restClient;
        this.metrics = metrics;
    }

    public Optional<AiServiceStatus> status() {
        long startedAt = System.nanoTime();
        try {
            AiServiceStatus response = restClient.get()
                .uri("/internal/ai/status")
                .retrieve()
                .body(AiServiceStatus.class);
            record("status", "success", startedAt);
            return Optional.ofNullable(response);
        } catch (RestClientException exception) {
            record("status", "error", startedAt);
            log.warn("Hammerly AI status check failed ({})", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public AiPlatformResponse<AiChatResponse> chat(AiChatRequest request, String userId) {
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<AiChatResponse> response = restClient.post()
                .uri("/internal/ai/chat")
                .header(InternalAiHeaders.USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> status.value() == 429,
                    (clientRequest, clientResponse) -> {
                        throw rateLimitExceeded(clientResponse.getHeaders());
                    })
                .toEntity(AiChatResponse.class);
            AiChatResponse body = response.getBody();
            if (body == null || body.answer() == null || body.answer().isBlank()) {
                throw new AiServiceUnavailableException("Hammerly AI returned an empty response.");
            }
            record("chat", "success", startedAt);
            return new AiPlatformResponse<>(body, rateLimit(response.getHeaders()));
        } catch (AiRateLimitExceededException | AiServiceUnavailableException exception) {
            record("chat", "error", startedAt);
            throw exception;
        } catch (RestClientException exception) {
            record("chat", "error", startedAt);
            log.warn("Hammerly AI chat call failed ({})", rootCauseName(exception));
            throw new AiServiceUnavailableException(UNAVAILABLE_MESSAGE, exception);
        }
    }

    public AiRateLimitStatus acquireStreamPermit(String userId) {
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<Void> response = restClient.post()
                .uri("/internal/ai/chat/rate-limit")
                .header(InternalAiHeaders.USER_ID, userId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(status -> status.value() == 429,
                    (clientRequest, clientResponse) -> {
                        throw rateLimitExceeded(clientResponse.getHeaders());
                    })
                .toBodilessEntity();
            record("rate_limit", "success", startedAt);
            return rateLimit(response.getHeaders());
        } catch (AiRateLimitExceededException exception) {
            record("rate_limit", "rejected", startedAt);
            throw exception;
        } catch (RestClientException exception) {
            record("rate_limit", "error", startedAt);
            log.warn("Hammerly AI rate-limit permit call failed ({})", rootCauseName(exception));
            throw new AiServiceUnavailableException(UNAVAILABLE_MESSAGE, exception);
        }
    }

    public void stream(AiChatRequest request, String userId, OutputStream browserOutput) {
        long startedAt = System.nanoTime();
        try {
            restClient.post()
                .uri("/internal/ai/chat/stream")
                .header(InternalAiHeaders.USER_ID, userId)
                .header(InternalAiHeaders.RATE_LIMIT_PRECHECKED, "true")
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
            record("stream", "success", startedAt);
        } catch (AiServiceUnavailableException exception) {
            record("stream", "error", startedAt);
            throw exception;
        } catch (RestClientException exception) {
            record("stream", "error", startedAt);
            log.warn("Hammerly AI stream proxy failed ({})", rootCauseName(exception));
            throw new AiServiceUnavailableException(UNAVAILABLE_MESSAGE, exception);
        }
    }

    private AiRateLimitExceededException rateLimitExceeded(HttpHeaders headers) {
        return new AiRateLimitExceededException(rateLimit(headers));
    }

    private AiRateLimitStatus rateLimit(HttpHeaders headers) {
        return new AiRateLimitStatus(
            integerHeader(headers, "X-RateLimit-Limit"),
            integerHeader(headers, "X-RateLimit-Remaining"),
            longHeader(headers, "X-RateLimit-Reset")
        );
    }

    private int integerHeader(HttpHeaders headers, String name) {
        try {
            return Integer.parseInt(Optional.ofNullable(headers.getFirst(name)).orElse("0"));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private long longHeader(HttpHeaders headers, String name) {
        try {
            return Long.parseLong(Optional.ofNullable(headers.getFirst(name)).orElse("0"));
        } catch (NumberFormatException exception) {
            return 0;
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

    private void record(String operation, String outcome, long startedAt) {
        if (metrics != null) metrics.completed(operation, outcome, startedAt);
    }
}
