package com.hammerly.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AiChatRole {
    @JsonProperty("user")
    USER,

    @JsonProperty("assistant")
    ASSISTANT
}
