package com.hammerly.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hammerly.backend.client.AiPlatformClient;
import com.hammerly.backend.dto.AiServiceStatus;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AiIntegrationControllerTest {
    private final AiPlatformClient client = mock(AiPlatformClient.class);
    private final AiIntegrationController controller = new AiIntegrationController(client);

    @Test
    void reportsReadyWithoutForwardingInternalConfiguration() {
        when(client.status()).thenReturn(Optional.of(new AiServiceStatus("hammerly-ai", "ready", false)));

        ResponseEntity<Map<String, Object>> response = controller.aiHealth();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("service", "hammerly-ai", "status", "ready", "aiConfigured", false),
            response.getBody());
    }

    @Test
    void reportsServiceUnavailableWithoutCrashingCore() {
        when(client.status()).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.aiHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(Map.of("service", "hammerly-ai", "status", "unavailable", "aiConfigured", false),
            response.getBody());
    }
}
