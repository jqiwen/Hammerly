package com.hammerly.worker.event;

import java.time.Instant;

public record MessageCreatedPayload(String role, String content, Instant createdAt) {
}
