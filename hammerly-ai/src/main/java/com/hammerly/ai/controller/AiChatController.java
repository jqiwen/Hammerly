package com.hammerly.ai.controller;

import com.hammerly.ai.dto.ChatRequest;
import com.hammerly.ai.dto.ChatResponse;
import com.hammerly.ai.dto.ChatStreamEvent;
import com.hammerly.ai.exception.AiExceptionHandler;
import com.hammerly.ai.service.AiChatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/internal/ai")
public class AiChatController {
    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(aiChatService.chat(request));
    }

    @PostMapping(value = "/chat/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<ChatStreamEvent>> stream(@Valid @RequestBody ChatRequest request) {
        try {
            return aiChatService.stream(request)
                .map(chunk -> ServerSentEvent.builder(new ChatStreamEvent(chunk))
                    .event("chunk").build())
                .concatWithValues(ServerSentEvent.builder(new ChatStreamEvent(""))
                    .event("done").build())
                .onErrorResume(exception -> {
                    log.warn("AI stream returned a safe error event ({})",
                        exception.getClass().getSimpleName());
                    return Flux.just(unavailableEvent());
                });
        } catch (RuntimeException exception) {
            log.warn("AI stream could not start and returned a safe error event ({})",
                exception.getClass().getSimpleName());
            return Flux.just(unavailableEvent());
        }
    }

    private ServerSentEvent<ChatStreamEvent> unavailableEvent() {
        return ServerSentEvent.builder(new ChatStreamEvent(AiExceptionHandler.UNAVAILABLE_MESSAGE))
            .event("error")
            .build();
    }
}
