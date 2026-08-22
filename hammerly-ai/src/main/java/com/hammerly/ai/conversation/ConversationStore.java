package com.hammerly.ai.conversation;

import java.util.List;

public interface ConversationStore {
    ConversationHistory getRecent(String userId, String conversationId);

    void append(String userId, String conversationId, List<ConversationMessage> messages);
}
