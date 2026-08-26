package com.hammerly.ai.controller;

import com.hammerly.ai.config.AiProviderAvailability;
import com.hammerly.ai.dto.AiStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ai")
public class AiStatusController {
    private final AiProviderAvailability providerAvailability;
    private final boolean redisEnabled;
    private final boolean kafkaEnabled;

    public AiStatusController(AiProviderAvailability providerAvailability,
                              @Value("${hammerly.redis.enabled:true}") boolean redisEnabled,
                              @Value("${hammerly.kafka.enabled:true}") boolean kafkaEnabled) {
        this.providerAvailability = providerAvailability;
        this.redisEnabled = redisEnabled;
        this.kafkaEnabled = kafkaEnabled;
    }

    @GetMapping("/status")
    AiStatusResponse status() {
        return new AiStatusResponse("hammerly-ai", "ready", providerAvailability.isConfigured(),
            redisEnabled, kafkaEnabled);
    }
}
