package com.hammerly.ai.rag;

import java.util.List;

public record RagResult(
    List<RagChunk> chunks,
    long knowledgeVersion,
    long knowledgeVersionDurationMs,
    long cacheDurationMs,
    long embeddingDurationMs,
    long searchDurationMs,
    boolean knowledgeVersionLocalHit,
    boolean cacheHit
) {
    public RagResult {
        chunks = List.copyOf(chunks);
    }

    public RagResult(List<RagChunk> chunks, long knowledgeVersion) {
        this(chunks, knowledgeVersion, 0, 0, 0, 0, false, false);
    }

    public static RagResult empty() {
        return new RagResult(List.of(), 0, 0, 0, 0, 0, false, false);
    }
}
