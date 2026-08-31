package com.hammerly.backend.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hammerly.backend.dto.AiChatMessage;
import com.hammerly.backend.dto.AiChatRequest;
import com.hammerly.backend.dto.AiChatResponse;
import com.hammerly.backend.dto.AiChatRole;
import com.hammerly.backend.dto.AiServiceStatus;
import com.hammerly.backend.exception.AiRateLimitExceededException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiPlatformClientTest {
    private static final String CONVERSATION_ID = "b29bd72b-a2d5-4938-90f0-151867ac4c7a";

    @Test
    void returnsSanitizedStatusPayloadFromAiService() {
        TestClient test = client();
        test.server().expect(once(), requestTo("http://hammerly-ai/internal/ai/status"))
            .andRespond(withSuccess(
                "{\"service\":\"hammerly-ai\",\"status\":\"ready\",\"aiConfigured\":false}",
                MediaType.APPLICATION_JSON));

        Optional<AiServiceStatus> result = test.client().status();

        assertTrue(result.isPresent());
        assertEquals("hammerly-ai", result.orElseThrow().service());
        test.server().verify();
    }

    @Test
    void returnsEmptyWhenAiServiceIsUnavailable() {
        TestClient test = client();
        test.server().expect(once(), requestTo("http://hammerly-ai/internal/ai/status"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertTrue(test.client().status().isEmpty());
        test.server().verify();
    }

    @Test
    void forwardsChatWithTrustedUserAndConversationIdentity() {
        TestClient test = client();
        test.server().expect(once(), requestTo("http://hammerly-ai/internal/ai/chat"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(InternalAiHeaders.USER_ID, "42"))
            .andExpect(content().json("""
                {"message":"What happens next?","conversationId":"%s","history":[
                  {"role":"assistant","content":"You won the auction."}
                ]}
                """.formatted(CONVERSATION_ID)))
            .andRespond(withSuccess("{\"answer\":\"Review the next steps.\"}",
                    MediaType.APPLICATION_JSON)
                .header("X-RateLimit-Limit", "20")
                .header("X-RateLimit-Remaining", "19"));

        AiPlatformResponse<AiChatResponse> response = test.client().chat(request(), "42");

        assertEquals("Review the next steps.", response.body().answer());
        assertEquals(19, response.rateLimit().remaining());
        test.server().verify();
    }

    @Test
    void acquiresPermitBeforeStreamingAndCopiesSseUnchanged() {
        TestClient test = client();
        test.server().expect(once(), requestTo("http://hammerly-ai/internal/ai/chat/rate-limit"))
            .andExpect(header(InternalAiHeaders.USER_ID, "42"))
            .andRespond(withStatus(HttpStatus.NO_CONTENT)
                .header("X-RateLimit-Limit", "20")
                .header("X-RateLimit-Remaining", "19"));
        String sse = "event:chunk\ndata:{\"content\":\"Bid now\"}\n\nevent:done\ndata:{\"content\":\"\"}\n\n";
        test.server().expect(once(), requestTo("http://hammerly-ai/internal/ai/chat/stream"))
            .andExpect(header(InternalAiHeaders.USER_ID, "42"))
            .andExpect(header(InternalAiHeaders.RATE_LIMIT_PRECHECKED, "true"))
            .andExpect(header(InternalAiHeaders.CORE_AI_STARTED_AT,
                org.hamcrest.Matchers.matchesPattern("\\d+")))
            .andRespond(withSuccess(sse, MediaType.TEXT_EVENT_STREAM));

        AiRateLimitStatus permit = test.client().acquireStreamPermit("42");
        ByteArrayOutputStream browserOutput = new ByteArrayOutputStream();
        test.client().stream(request(), "42", browserOutput);

        assertEquals(19, permit.remaining());
        assertEquals(sse, browserOutput.toString(StandardCharsets.UTF_8));
        test.server().verify();
    }

    @Test
    void rateLimitResponseMapsToDedicatedException() {
        TestClient test = client();
        test.server().expect(once(), requestTo("http://hammerly-ai/internal/ai/chat"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit", "20")
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", "1700000060"));

        AiRateLimitExceededException exception = assertThrows(AiRateLimitExceededException.class,
            () -> test.client().chat(request(), "42"));

        assertEquals(20, exception.rateLimit().limit());
        assertEquals(0, exception.rateLimit().remaining());
        test.server().verify();
    }

    private AiChatRequest request() {
        return new AiChatRequest("What happens next?", List.of(
            new AiChatMessage(AiChatRole.ASSISTANT, "You won the auction.")
        ), CONVERSATION_ID);
    }

    private TestClient client() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hammerly-ai");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new TestClient(new AiPlatformClient(builder.build()), server);
    }

    private record TestClient(AiPlatformClient client, MockRestServiceServer server) {
    }
}
