package com.hammerly.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AiChatRequest(
    @NotBlank @Size(max = 2_000) String message,
    @Size(max = 20) List<@Valid AiChatMessage> history,
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    String conversationId,
    boolean standaloneFaq
) {
    public AiChatRequest(String message, List<AiChatMessage> history) {
        this(message, history, null, false);
    }

    public AiChatRequest(String message, List<AiChatMessage> history, String conversationId) {
        this(message, history, conversationId, false);
    }

    public AiChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
        conversationId = conversationId == null || conversationId.isBlank()
            ? UUID.randomUUID().toString()
            : conversationId;
    }
}
