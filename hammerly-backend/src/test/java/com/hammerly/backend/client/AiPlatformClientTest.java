package com.hammerly.backend.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hammerly.backend.dto.AiChatMessage;
import com.hammerly.backend.dto.AiChatRequest;
import com.hammerly.backend.dto.AiChatResponse;
import com.hammerly.backend.dto.AiChatRole;
import com.hammerly.backend.dto.AiServiceStatus;
import com.hammerly.backend.exception.AiServiceUnavailableException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiPlatformClientTest {
    @Test
    void returnsSanitizedStatusPayloadFromAiService() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hammerly-ai");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiPlatformClient client = new AiPlatformClient(builder.build());
        server.expect(once(), requestTo("http://hammerly-ai/internal/ai/status"))
            .andRespond(withSuccess(
                "{\"service\":\"hammerly-ai\",\"status\":\"ready\",\"aiConfigured\":false}",
                MediaType.APPLICATION_JSON));

        Optional<AiServiceStatus> result = client.status();

        assertTrue(result.isPresent());
        assertEquals("hammerly-ai", result.orElseThrow().service());
        assertEquals("ready", result.orElseThrow().status());
        assertEquals(false, result.orElseThrow().aiConfigured());
        server.verify();
    }

    @Test
    void returnsEmptyWhenAiServiceIsUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hammerly-ai");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiPlatformClient client = new AiPlatformClient(builder.build());
        server.expect(once(), requestTo("http://hammerly-ai/internal/ai/status"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertTrue(client.status().isEmpty());
        server.verify();
    }

    @Test
    void forwardsChatMessageAndHistoryToInternalAiApi() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hammerly-ai");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiPlatformClient client = new AiPlatformClient(builder.build());
        server.expect(once(), requestTo("http://hammerly-ai/internal/ai/chat"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""
                {"message":"What happens next?","history":[
                  {"role":"assistant","content":"You won the auction."}
                ]}
                """))
            .andRespond(withSuccess("{\"answer\":\"Review the next steps.\"}",
                MediaType.APPLICATION_JSON));

        AiChatResponse response = client.chat(new AiChatRequest("What happens next?", List.of(
            new AiChatMessage(AiChatRole.ASSISTANT, "You won the auction.")
        )));

        assertEquals("Review the next steps.", response.answer());
        server.verify();
    }

    @Test
    void copiesStreamingSseResponseWithoutChangingItsBytes() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hammerly-ai");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiPlatformClient client = new AiPlatformClient(builder.build());
        String sse = "event:chunk\ndata:{\"content\":\"Bid now\"}\n\nevent:done\ndata:{\"content\":\"\"}\n\n";
        server.expect(once(), requestTo("http://hammerly-ai/internal/ai/chat/stream"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(sse, MediaType.TEXT_EVENT_STREAM));
        ByteArrayOutputStream browserOutput = new ByteArrayOutputStream();

        client.stream(new AiChatRequest("How do I bid?", List.of()), browserOutput);

        assertEquals(sse, browserOutput.toString(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void chatFailureMapsToAiServiceUnavailableException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hammerly-ai");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiPlatformClient client = new AiPlatformClient(builder.build());
        server.expect(once(), requestTo("http://hammerly-ai/internal/ai/chat"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(AiServiceUnavailableException.class,
            () -> client.chat(new AiChatRequest("Help", List.of())));
        server.verify();
    }
}
