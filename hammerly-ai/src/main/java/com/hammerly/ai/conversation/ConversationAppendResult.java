package com.hammerly.ai.conversation;

public record ConversationAppendResult(boolean successful, int messageCount) {
    public static ConversationAppendResult success(int messageCount) {
        return new ConversationAppendResult(true, messageCount);
    }

    public static ConversationAppendResult failure() {
        return new ConversationAppendResult(false, 0);
    }
}
