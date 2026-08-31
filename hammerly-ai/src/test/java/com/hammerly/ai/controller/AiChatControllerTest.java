package com.hammerly.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hammerly.ai.dto.ChatRequest;
import com.hammerly.ai.exception.AiExceptionHandler;
import com.hammerly.ai.exception.AiProviderUnavailableException;
import com.hammerly.ai.exception.AiConcurrencyLimitException;
import com.hammerly.ai.exception.AiRateLimitExceededException;
import com.hammerly.ai.ratelimit.RateLimitDecision;
import com.hammerly.ai.service.AiChatResult;
import com.hammerly.ai.service.AiChatService;
import com.hammerly.ai.service.AiStreamResult;
import com.hammerly.ai.rag.RagSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

@WebMvcTest(AiChatController.class)
class AiChatControllerTest {
    private static final String CONVERSATION_ID = "b29bd72b-a2d5-4938-90f0-151867ac4c7a";
    private static final RateLimitDecision ALLOWED =
        new RateLimitDecision(true, 20, 19, 1_700_000_060L, true);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AiChatService aiChatService;

    @Test
    void validChatUsesTrustedUserHeaderAndReturnsRateLimitHeaders() throws Exception {
        when(aiChatService.chat(anyString(), any()))
            .thenReturn(new AiChatResult("Open an active auction and enter your bid.", ALLOWED));

        mvc.perform(post("/internal/ai/chat")
                .header(InternalAiHeaders.USER_ID, "42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"How do I bid?","conversationId":"%s","history":[
                      {"role":"user","content":"I found an auction."},
                      {"role":"assistant","content":"Great."}
                    ]}
                    """.formatted(CONVERSATION_ID)))
            .andExpect(status().isOk())
            .andExpect(header().string("X-RateLimit-Limit", "20"))
            .andExpect(jsonPath("$.answer")
                .value("Open an active auction and enter your bid."));

        verify(aiChatService).chat(anyString(), any(ChatRequest.class));
    }

    @Test
    void missingTrustedUserHeaderIsRejected() throws Exception {
        mvc.perform(post("/internal/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Help\",\"history\":[]}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(aiChatService);
    }

    @Test
    void blankAndOversizedMessagesAreRejectedBeforeProviderCall() throws Exception {
        mvc.perform(post("/internal/ai/chat")
                .header(InternalAiHeaders.USER_ID, "42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"   \",\"history\":[]}"))
            .andExpect(status().isBadRequest());

        String oversized = "x".repeat(2_001);
        mvc.perform(post("/internal/ai/chat")
                .header(InternalAiHeaders.USER_ID, "42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + oversized + "\",\"history\":[]}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(aiChatService);
    }

    @Test
    void providerErrorMapsToSafeApiError() throws Exception {
        when(aiChatService.chat(anyString(), any()))
            .thenThrow(new AiProviderUnavailableException("secret provider detail"));

        mvc.perform(post("/internal/ai/chat")
                .header(InternalAiHeaders.USER_ID, "42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"How do I bid?\",\"history\":[]}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value(AiExceptionHandler.UNAVAILABLE_MESSAGE))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("secret provider detail"))));
    }

    @Test
    void rateLimitReturns429WithStableBodyAndHeaders() throws Exception {
        RateLimitDecision rejected = new RateLimitDecision(false, 20, 0, 1_700_000_060L, true);
        when(aiChatService.chat(anyString(), any()))
            .thenThrow(new AiRateLimitExceededException(rejected));

        mvc.perform(post("/internal/ai/chat")
                .header(InternalAiHeaders.USER_ID, "42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Help\",\"history\":[]}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("X-RateLimit-Remaining", "0"))
            .andExpect(jsonPath("$.error").value("AI_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void streamingEndpointEmitsIncrementalSseEvents() throws Exception {
        when(aiChatService.stream(anyString(), any(), anyBoolean(), any()))
            .thenReturn(new AiStreamResult(Flux.just("Place ", "a bid."), ALLOWED));

        MvcResult started = mvc.perform(post("/internal/ai/chat/stream")
                .header(InternalAiHeaders.USER_ID, "42")
                .header(InternalAiHeaders.CORE_AI_STARTED_AT, "1700000000000")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"message\":\"How do I bid?\",\"history\":[]}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chunk")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));

        verify(aiChatService).stream(anyString(), any(ChatRequest.class), anyBoolean(),
            eq(1_700_000_000_000L));
    }

    @Test
    void cachedSingleChunkPreservesSseContract() throws Exception {
        when(aiChatService.stream(anyString(), any(), anyBoolean(), any()))
            .thenReturn(new AiStreamResult(Flux.just("Cached answer"), ALLOWED));

        MvcResult started = mvc.perform(post("/internal/ai/chat/stream")
                .header(InternalAiHeaders.USER_ID, "42")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"message\":\"Help\",\"history\":[]}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Cached answer")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    }

    @Test
    void streamingEndpointEmitsRealSourceMetadataBeforeChunks() throws Exception {
        when(aiChatService.stream(anyString(), any(), anyBoolean(), any()))
            .thenReturn(new AiStreamResult(Flux.just("Bid now."), ALLOWED,
                List.of(new RagSource("Hammerly Support Guide", "Bidding", "chunk-1"))));

        MvcResult started = mvc.perform(post("/internal/ai/chat/stream")
                .header(InternalAiHeaders.USER_ID, "42")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"message\":\"How do I bid?\",\"history\":[]}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:metadata")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Hammerly Support Guide")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chunk")));
    }

    @Test
    void bulkheadFailureMapsToSafeBusySseEvent() throws Exception {
        when(aiChatService.stream(anyString(), any(), anyBoolean(), any()))
            .thenReturn(new AiStreamResult(Flux.error(new AiConcurrencyLimitException(
                new IllegalStateException("internal bulkhead detail"))), ALLOWED));

        MvcResult started = mvc.perform(post("/internal/ai/chat/stream")
                .header(InternalAiHeaders.USER_ID, "42")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"message\":\"Help\",\"history\":[]}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                AiConcurrencyLimitException.MESSAGE)))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("internal bulkhead detail"))));
    }
}
