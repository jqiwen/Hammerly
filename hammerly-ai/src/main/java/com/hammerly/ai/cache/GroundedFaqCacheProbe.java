package com.hammerly.ai.cache;

import java.util.Optional;

public record GroundedFaqCacheProbe(
    Optional<GroundedFaqCacheEntry> entry,
    long knowledgeBaseVersion,
    long durationMs,
    boolean available
) {
    public GroundedFaqCacheProbe {
        entry = entry == null ? Optional.empty() : entry;
    }

    public static GroundedFaqCacheProbe unavailable() {
        return new GroundedFaqCacheProbe(Optional.empty(), 0, 0, false);
    }
}
