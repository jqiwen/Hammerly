package com.hammerly.ai.service;

import com.hammerly.ai.dto.ChatMessage;
import java.util.List;

/** Bounded model input with trusted system context kept separate from user content. */
public record ModelRequest(
    List<ChatMessage> history,
    String question,
    String systemContext
) {
    public ModelRequest {
        history = List.copyOf(history);
        systemContext = systemContext == null ? "" : systemContext;
    }
}

