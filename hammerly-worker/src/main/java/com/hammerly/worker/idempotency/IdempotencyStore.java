package com.hammerly.worker.idempotency;

import java.util.UUID;

public interface IdempotencyStore {
    ProcessingClaim claim(UUID eventId);

    void complete(ProcessingClaim claim);

    void release(ProcessingClaim claim);
}
