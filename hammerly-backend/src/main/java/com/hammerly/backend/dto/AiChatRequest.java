package com.hammerly.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AiChatRequest(
    @NotBlank @Size(max = 2_000) String message,
    @Size(max = 20) List<@Valid AiChatMessage> history
) {
    public AiChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
