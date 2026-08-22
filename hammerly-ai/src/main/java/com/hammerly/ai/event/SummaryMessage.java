package com.hammerly.ai.event;

import com.hammerly.ai.dto.ChatRole;
import java.time.Instant;

public record SummaryMessage(ChatRole role, String content, Instant createdAt) {
}
