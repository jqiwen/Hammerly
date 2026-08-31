package com.hammerly.ai.rag;

import java.util.List;

public record RagResult(
    List<RagChunk> chunks,
    long knowledgeVersion,
    long embeddingDurationMs,
    long searchDurationMs,
    boolean cacheHit
) {
    public RagResult {
        chunks = List.copyOf(chunks);
    }

    public RagResult(List<RagChunk> chunks, long knowledgeVersion) {
        this(chunks, knowledgeVersion, 0, 0, false);
    }

    public RagResult asCacheHit() {
        return new RagResult(chunks, knowledgeVersion, 0, 0, true);
    }

    public static RagResult empty() {
        return new RagResult(List.of(), 0, 0, 0, false);
    }
}
