package com.hammerly.backend.controller;

import com.hammerly.backend.client.AiPlatformClient;
import com.hammerly.backend.dto.AiServiceStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/integration")
@ConditionalOnProperty(name = "hammerly.ai.diagnostic-enabled", havingValue = "true")
public class AiIntegrationController {
    private final AiPlatformClient aiPlatformClient;

    public AiIntegrationController(AiPlatformClient aiPlatformClient) {
        this.aiPlatformClient = aiPlatformClient;
    }

    @GetMapping("/ai-health")
    ResponseEntity<Map<String, Object>> aiHealth() {
        return aiPlatformClient.status()
            .map(status -> ResponseEntity.ok(sanitized(status)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(unavailable()));
    }

    private Map<String, Object> sanitized(AiServiceStatus status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", status.service());
        response.put("status", status.status());
        response.put("aiConfigured", status.aiConfigured());
        return response;
    }

    private Map<String, Object> unavailable() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "hammerly-ai");
        response.put("status", "unavailable");
        response.put("aiConfigured", false);
        return response;
    }
}
