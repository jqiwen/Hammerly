package com.hammerly.ai.event;

import java.util.List;

public record ConversationSummaryRequestedPayload(
    int messageCount,
    List<SummaryMessage> messages
) {
    public ConversationSummaryRequestedPayload {
        messages = List.copyOf(messages);
    }
}
