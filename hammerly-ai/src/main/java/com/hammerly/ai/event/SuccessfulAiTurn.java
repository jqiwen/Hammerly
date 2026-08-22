package com.hammerly.ai.event;

import java.time.Instant;
import java.util.List;

public record SuccessfulAiTurn(
    String userId,
    String conversationId,
    String question,
    String answer,
    Instant occurredAt,
    int storedMessageCount,
    List<SummaryMessage> conversationSnapshot
) {
    public SuccessfulAiTurn {
        conversationSnapshot = List.copyOf(conversationSnapshot);
    }
}
