package com.hammerly.worker.event;

import java.time.Instant;

public record SummaryMessage(String role, String content, Instant createdAt) {
}
