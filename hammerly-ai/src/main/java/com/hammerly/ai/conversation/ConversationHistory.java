package com.hammerly.ai.conversation;

import java.util.List;

public record ConversationHistory(List<ConversationMessage> messages, boolean redisAvailable) {
    public ConversationHistory {
        messages = List.copyOf(messages);
    }

    public static ConversationHistory available(List<ConversationMessage> messages) {
        return new ConversationHistory(messages, true);
    }

    public static ConversationHistory unavailable() {
        return new ConversationHistory(List.of(), false);
    }
}
