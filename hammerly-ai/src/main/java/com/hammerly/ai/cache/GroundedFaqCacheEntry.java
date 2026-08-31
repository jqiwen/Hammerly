package com.hammerly.ai.cache;

import com.hammerly.ai.rag.RagSource;
import java.util.List;

public record GroundedFaqCacheEntry(
    String answer,
    List<RagSource> sources,
    long knowledgeBaseVersion,
    String model,
    String configVersion
) {
    public GroundedFaqCacheEntry {
        sources = List.copyOf(sources);
    }
}
