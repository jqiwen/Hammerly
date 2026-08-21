package com.hammerly.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiChatMessage(
    @NotNull AiChatRole role,
    @NotBlank @Size(max = 4_000) String content
) {
}
