package com.hammerly.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.hammerly.backend.client.AiPlatformResponse;
import com.hammerly.backend.client.AiRateLimitStatus;
import com.hammerly.backend.dto.AiChatRequest;
import com.hammerly.backend.dto.AiChatResponse;
import com.hammerly.backend.exception.AiServiceUnavailableException;
import com.hammerly.backend.exception.AiRateLimitExceededException;
import com.hammerly.backend.exception.GlobalExceptionHandler;
import com.hammerly.backend.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AiSupportControllerTest {
    private AiPlatformClient client;
    private AiSupportController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        client = mock(AiPlatformClient.class);
        when(client.acquireStreamPermit(anyString()))
            .thenReturn(new AiRateLimitStatus(20, 19, 1_700_000_060L));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        controller = new AiSupportController(client);
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setValidator(validator)
            .build();
    }

    @Test
    void validRequestIsForwardedToAiClient() throws Exception {
        when(client.chat(any(), anyString())).thenReturn(new AiPlatformResponse<>(
            new AiChatResponse("Use the bid form."),
            new AiRateLimitStatus(20, 19, 1_700_000_060L)));

        mvc.perform(post("/api/ai/support/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"How do I bid?","history":[
                      {"role":"assistant","content":"What can I help with?"}
                    ]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Use the bid form."));

        verify(client).chat(any(AiChatRequest.class), anyString());
    }

    @Test
    void authenticatedPrincipalBecomesTrustedInternalUserId() {
        AiChatRequest request = new AiChatRequest("Help", java.util.List.of(),
            "b29bd72b-a2d5-4938-90f0-151867ac4c7a");
        when(client.chat(request, "42")).thenReturn(new AiPlatformResponse<>(
            new AiChatResponse("Answer"), new AiRateLimitStatus(20, 19, 1_700_000_060L)));

        controller.chat(request, new AuthenticatedUser(42L, "user@example.com"));

        verify(client).chat(request, "42");
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
        when(client.chat(any(), anyString()))
            .thenThrow(new AiServiceUnavailableException("connection refused"));

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
            java.io.OutputStream output = invocation.getArgument(2);
            output.write(("event:sources\ndata:{\"sources\":[{\"title\":\"Bidding\","
                + "\"source\":\"Hammerly Support Guide\"}]}\n\n"
                + "event:chunk\ndata:Bid now\n\nevent:done\ndata:\n\n")
                .getBytes(StandardCharsets.UTF_8));
            output.flush();
            return null;
        }).when(client).stream(any(AiChatRequest.class), anyString(),
            any(java.io.OutputStream.class), anyLong());

        MvcResult started = mvc.perform(post("/api/ai/support/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .content("{\"message\":\"How do I bid?\",\"history\":[]}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:sources")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "Hammerly Support Guide")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chunk")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    }

    @Test
    void streamingFailureReturnsSafeSseErrorWithoutCrashing() throws Exception {
        org.mockito.Mockito.doThrow(new AiServiceUnavailableException("provider 500"))
            .when(client).stream(any(AiChatRequest.class), anyString(),
                any(java.io.OutputStream.class), anyLong());

        MvcResult started = mvc.perform(post("/api/ai/support/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
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

    @Test
    void streamingRateLimitIsReturnedBeforeSseStarts() throws Exception {
        when(client.acquireStreamPermit(anyString())).thenThrow(new AiRateLimitExceededException(
            new AiRateLimitStatus(20, 0, 1_700_000_060L)));

        mvc.perform(post("/api/ai/support/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .content("{\"message\":\"How do I bid?\",\"history\":[]}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error").value("AI_RATE_LIMIT_EXCEEDED"));
    }
}
