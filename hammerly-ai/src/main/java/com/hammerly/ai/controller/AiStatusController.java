package com.hammerly.ai.controller;

import com.hammerly.ai.config.AiProviderAvailability;
import com.hammerly.ai.dto.AiStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ai")
public class AiStatusController {
    private final AiProviderAvailability providerAvailability;

    public AiStatusController(AiProviderAvailability providerAvailability) {
        this.providerAvailability = providerAvailability;
    }

    @GetMapping("/status")
    AiStatusResponse status() {
        return new AiStatusResponse("hammerly-ai", "ready", providerAvailability.isConfigured());
    }
}
