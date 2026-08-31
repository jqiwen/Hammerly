package com.hammerly.ai.context;

import com.hammerly.ai.dto.ChatMessage;
import java.util.List;

public interface AiContextBuilder {
    BuiltAiContext build(String userId, String conversationId,
                         List<ChatMessage> storedContext, String question);

    static AiContextBuilder basic() {
        return (userId, conversationId, storedContext, question) ->
            new BuiltAiContext(storedContext, question, List.of());
    }
}
