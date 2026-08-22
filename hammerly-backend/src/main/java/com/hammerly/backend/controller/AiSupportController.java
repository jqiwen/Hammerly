package com.hammerly.backend.controller;

import com.hammerly.backend.client.AiPlatformClient;
import com.hammerly.backend.client.AiPlatformResponse;
import com.hammerly.backend.client.AiRateLimitStatus;
import com.hammerly.backend.dto.AiChatRequest;
import com.hammerly.backend.dto.AiChatResponse;
import com.hammerly.backend.exception.AiServiceUnavailableException;
import com.hammerly.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/ai/support")
public class AiSupportController {
    public static final String UNAVAILABLE_MESSAGE =
        "Hammerly AI is temporarily unavailable. Please try again.";

    private static final Logger log = LoggerFactory.getLogger(AiSupportController.class);
    private static final byte[] UNAVAILABLE_EVENT = (
        "event:error\n" +
        "data:{\"content\":\"" + UNAVAILABLE_MESSAGE + "\"}\n\n"
    ).getBytes(StandardCharsets.UTF_8);

    private final AiPlatformClient aiPlatformClient;

    public AiSupportController(AiPlatformClient aiPlatformClient) {
        this.aiPlatformClient = aiPlatformClient;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        String userId = trustedUserId(user, request.conversationId());
        AiPlatformResponse<AiChatResponse> result = aiPlatformClient.chat(request, userId);
        return ResponseEntity.ok()
            .headers(rateLimitHeaders(result.rateLimit()))
            .header("X-Hammerly-Conversation-Id", request.conversationId())
            .body(result.body());
    }

    @PostMapping(value = "/chat/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<StreamingResponseBody> stream(
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        String userId = trustedUserId(user, request.conversationId());
        AiRateLimitStatus rateLimit = aiPlatformClient.acquireStreamPermit(userId);
        StreamingResponseBody body = output -> {
            try {
                aiPlatformClient.stream(request, userId, output);
            } catch (AiServiceUnavailableException exception) {
                log.warn("Returning safe AI unavailable SSE event ({})",
                    exception.getClass().getSimpleName());
                try {
                    output.write(UNAVAILABLE_EVENT);
                    output.flush();
                } catch (IOException disconnectedBrowser) {
                    log.debug("Browser disconnected before AI error event could be delivered");
                }
            }
        };

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .cacheControl(CacheControl.noStore())
            .header("X-Accel-Buffering", "no")
            .header("X-Hammerly-Conversation-Id", request.conversationId())
            .headers(rateLimitHeaders(rateLimit))
            .body(body);
    }

    private String trustedUserId(AuthenticatedUser user, String conversationId) {
        return user == null ? "guest-" + conversationId : Long.toString(user.userId());
    }

    private HttpHeaders rateLimitHeaders(AiRateLimitStatus rateLimit) {
        HttpHeaders headers = new HttpHeaders();
        if (rateLimit != null && rateLimit.limit() > 0) {
            headers.set("X-RateLimit-Limit", Integer.toString(rateLimit.limit()));
            headers.set("X-RateLimit-Remaining", Integer.toString(rateLimit.remaining()));
            headers.set("X-RateLimit-Reset", Long.toString(rateLimit.resetEpochSeconds()));
        }
        return headers;
    }
}
