package com.hammerly.backend.controller;

import com.hammerly.backend.client.AiPlatformClient;
import com.hammerly.backend.dto.AiChatRequest;
import com.hammerly.backend.dto.AiChatResponse;
import com.hammerly.backend.exception.AiServiceUnavailableException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return aiPlatformClient.chat(request);
    }

    @PostMapping(value = "/chat/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<StreamingResponseBody> stream(@Valid @RequestBody AiChatRequest request) {
        StreamingResponseBody body = output -> {
            try {
                aiPlatformClient.stream(request, output);
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
            .body(body);
    }
}
