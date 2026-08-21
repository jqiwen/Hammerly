package com.hammerly.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hammerly.backend.client.AiPlatformClient;
import com.hammerly.backend.dto.AiChatRequest;
import com.hammerly.backend.dto.AiChatResponse;
import com.hammerly.backend.exception.AiServiceUnavailableException;
import com.hammerly.backend.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AiSupportControllerTest {
    private AiPlatformClient client;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        client = mock(AiPlatformClient.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AiSupportController(client))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator)
            .build();
    }

    @Test
    void validRequestIsForwardedToAiClient() throws Exception {
        when(client.chat(any())).thenReturn(new AiChatResponse("Use the bid form."));

        mvc.perform(post("/api/ai/support/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"How do I bid?","history":[
                      {"role":"assistant","content":"What can I help with?"}
                    ]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Use the bid form."));

        verify(client).chat(any(AiChatRequest.class));
    }

    @Test
    void invalidRequestIsRejectedBeforeDownstreamCall() throws Exception {
        mvc.perform(post("/api/ai/support/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\" \",\"history\":[]}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(client);
    }

    @Test
    void unavailableAiReturnsFriendlyServiceUnavailableResponse() throws Exception {
        when(client.chat(any())).thenThrow(new AiServiceUnavailableException("connection refused"));

        mvc.perform(post("/api/ai/support/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Help me bid\",\"history\":[]}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value(AiSupportController.UNAVAILABLE_MESSAGE))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("connection refused"))));
    }

    @Test
    void streamingEndpointUsesSseAndForwardsBytes() throws Exception {
        doAnswer(invocation -> {
            java.io.OutputStream output = invocation.getArgument(1);
            output.write("event:chunk\ndata:Bid now\n\nevent:done\ndata:\n\n"
                .getBytes(StandardCharsets.UTF_8));
            output.flush();
            return null;
        }).when(client).stream(any(AiChatRequest.class), any(java.io.OutputStream.class));

        MvcResult started = mvc.perform(post("/api/ai/support/chat/stream")
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

    @Test
    void streamingFailureReturnsSafeSseErrorWithoutCrashing() throws Exception {
        org.mockito.Mockito.doThrow(new AiServiceUnavailableException("provider 500"))
            .when(client).stream(any(AiChatRequest.class), any(java.io.OutputStream.class));

        MvcResult started = mvc.perform(post("/api/ai/support/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"message\":\"How do I bid?\",\"history\":[]}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:error")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                AiSupportController.UNAVAILABLE_MESSAGE)))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("provider 500"))));
    }
}
