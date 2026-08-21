package com.hammerly.backend.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hammerly.backend.dto.AiServiceStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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
}
