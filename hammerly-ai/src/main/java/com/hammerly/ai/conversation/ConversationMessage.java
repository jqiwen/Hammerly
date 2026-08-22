package com.hammerly.ai.conversation;

import com.hammerly.ai.dto.ChatRole;
import java.time.Instant;

public record ConversationMessage(ChatRole role, String content, Instant timestamp) {
}
