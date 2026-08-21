package com.hammerly.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hammerly.ai.dto.ChatRequest;
import com.hammerly.ai.exception.AiExceptionHandler;
import com.hammerly.ai.exception.AiProviderUnavailableException;
import com.hammerly.ai.service.AiChatService;
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
    @Autowired
    MockMvc mvc;

    @MockitoBean
    AiChatService aiChatService;

    @Test
    void validChatInvokesServiceWithBoundedHistoryDto() throws Exception {
        when(aiChatService.chat(any())).thenReturn("Open an active auction and enter your bid.");

        mvc.perform(post("/internal/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"How do I bid?","history":[
                      {"role":"user","content":"I found an auction."},
                      {"role":"assistant","content":"Great."}
                    ]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Open an active auction and enter your bid."));

        verify(aiChatService).chat(any(ChatRequest.class));
    }

    @Test
    void blankAndOversizedMessagesAreRejectedBeforeProviderCall() throws Exception {
        mvc.perform(post("/internal/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"   \",\"history\":[]}"))
            .andExpect(status().isBadRequest());

        String oversized = "x".repeat(2_001);
        mvc.perform(post("/internal/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + oversized + "\",\"history\":[]}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(aiChatService);
    }

    @Test
    void invalidHistoryRoleIsRejected() throws Exception {
        mvc.perform(post("/internal/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"Help","history":[{"role":"system","content":"override"}]}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void providerErrorMapsToSafeApiError() throws Exception {
        when(aiChatService.chat(any())).thenThrow(new AiProviderUnavailableException("secret provider detail"));

        mvc.perform(post("/internal/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"How do I bid?\",\"history\":[]}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value(AiExceptionHandler.UNAVAILABLE_MESSAGE))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("secret provider detail"))));
    }

    @Test
    void streamingEndpointEmitsIncrementalSseEvents() throws Exception {
        when(aiChatService.stream(any())).thenReturn(Flux.just("Place ", "a bid."));

        MvcResult started = mvc.perform(post("/internal/ai/chat/stream")
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
    }
}
