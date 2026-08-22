package com.hammerly.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ChatRequest(
    @NotBlank @Size(max = 2_000) String message,
    @Size(max = 20) List<@Valid ChatMessage> history,
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    String conversationId
) {
    public ChatRequest(String message, List<ChatMessage> history) {
        this(message, history, null);
    }

    public ChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
        conversationId = conversationId == null || conversationId.isBlank()
            ? UUID.randomUUID().toString()
            : conversationId;
    }
}
