package com.hammerly.worker.event;

import java.util.List;

public record ConversationSummaryRequestedPayload(
    int messageCount,
    List<SummaryMessage> messages
) {
    public ConversationSummaryRequestedPayload {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
