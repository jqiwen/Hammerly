package com.hammerly.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatRequest(
    @NotBlank @Size(max = 2_000) String message,
    @Size(max = 20) List<@Valid ChatMessage> history
) {
    public ChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
