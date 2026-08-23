package com.hammerly.ai.controller;

import com.hammerly.ai.dto.ChatRequest;
import com.hammerly.ai.dto.ChatResponse;
import com.hammerly.ai.dto.ChatStreamEvent;
import com.hammerly.ai.exception.AiExceptionHandler;
import com.hammerly.ai.exception.AiRateLimitExceededException;
import com.hammerly.ai.ratelimit.RateLimitDecision;
import com.hammerly.ai.service.AiChatResult;
import com.hammerly.ai.service.AiChatService;
import com.hammerly.ai.service.AiStreamResult;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/internal/ai")
public class AiChatController {
    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);
    private static final Pattern TRUSTED_USER_ID = Pattern.compile("^[A-Za-z0-9-]{1,80}$");

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ChatResponse> chat(
            @RequestHeader(InternalAiHeaders.USER_ID) String userId,
            @Valid @RequestBody ChatRequest request) {
        AiChatResult result = aiChatService.chat(validateUserId(userId), request);
        return ResponseEntity.ok()
            .headers(rateLimitHeaders(result.rateLimit()))
            .body(new ChatResponse(result.answer()));
    }

    @PostMapping(value = "/chat/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<Flux<ServerSentEvent<ChatStreamEvent>>> stream(
            @RequestHeader(InternalAiHeaders.USER_ID) String userId,
            @RequestHeader(name = InternalAiHeaders.RATE_LIMIT_PRECHECKED,
                defaultValue = "false") boolean permitAlreadyAcquired,
            @Valid @RequestBody ChatRequest request) {
        final AiStreamResult result;
        try {
            result = aiChatService.stream(validateUserId(userId), request, permitAlreadyAcquired);
        } catch (AiRateLimitExceededException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("AI stream could not start and returned a safe error event ({})",
                exception.getClass().getSimpleName());
            return ResponseEntity.ok(Flux.just(unavailableEvent(exception)));
        }

        Flux<ServerSentEvent<ChatStreamEvent>> events = result.chunks()
            .map(chunk -> ServerSentEvent.builder(new ChatStreamEvent(chunk))
                .event("chunk").build())
            .concatWithValues(ServerSentEvent.builder(new ChatStreamEvent(""))
                .event("done").build())
            .onErrorResume(exception -> {
                log.warn("AI stream returned a safe error event ({})",
                    exception.getClass().getSimpleName());
                return Flux.just(unavailableEvent(exception));
            });
        return ResponseEntity.ok()
            .headers(rateLimitHeaders(result.rateLimit()))
            .body(events);
    }

    @PostMapping(value = "/chat/rate-limit", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> acquireStreamPermit(
            @RequestHeader(InternalAiHeaders.USER_ID) String userId) {
        RateLimitDecision decision = aiChatService.acquirePermit(validateUserId(userId));
        return ResponseEntity.noContent().headers(rateLimitHeaders(decision)).build();
    }

    private String validateUserId(String userId) {
        if (!TRUSTED_USER_ID.matcher(userId).matches()) {
            throw new IllegalArgumentException("Invalid internal user identifier");
        }
        return userId;
    }

    private HttpHeaders rateLimitHeaders(RateLimitDecision decision) {
        HttpHeaders headers = new HttpHeaders();
        if (decision.limit() > 0) {
            headers.set("X-RateLimit-Limit", Integer.toString(decision.limit()));
            headers.set("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
            headers.set("X-RateLimit-Reset", Long.toString(decision.resetEpochSeconds()));
        }
        return headers;
    }

    private ServerSentEvent<ChatStreamEvent> unavailableEvent(Throwable failure) {
        return ServerSentEvent.builder(new ChatStreamEvent(AiExceptionHandler.safeMessage(failure)))
            .event("error")
            .build();
    }
}
