package com.hammerly.ai.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of(
            "status", "AI service is running",
            "service", "hammerly-ai"
        );
    }
}
