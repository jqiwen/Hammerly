package com.hammerly.ai.context;

import com.hammerly.ai.dto.ChatMessage;
import com.hammerly.ai.rag.RagSource;
import java.util.List;

public record BuiltAiContext(
    List<ChatMessage> messages,
    String modelQuestion,
    List<RagSource> sources
) {
    public BuiltAiContext {
        messages = List.copyOf(messages);
        sources = List.copyOf(sources);
    }
}
