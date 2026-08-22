package com.hammerly.worker.summary;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummary(
    String userId,
    String conversationId,
    int sourceMessageCount,
    String summary,
    Instant generatedAt,
    UUID sourceEventId
) {
}
