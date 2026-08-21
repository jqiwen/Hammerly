package com.hammerly.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatMessage(
    @NotNull ChatRole role,
    @NotBlank @Size(max = 4_000) String content
) {
}
