package com.hammerly.worker.idempotency;

import java.util.UUID;

public record ProcessingClaim(UUID eventId, Status status, String lockToken) {
    public enum Status {
        ACQUIRED,
        ALREADY_PROCESSED,
        IN_PROGRESS
    }
}
