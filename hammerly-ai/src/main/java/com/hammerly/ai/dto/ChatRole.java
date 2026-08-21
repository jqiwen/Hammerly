package com.hammerly.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ChatRole {
    @JsonProperty("user")
    USER,

    @JsonProperty("assistant")
    ASSISTANT
}
