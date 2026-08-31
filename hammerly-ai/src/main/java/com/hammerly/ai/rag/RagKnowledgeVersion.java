package com.hammerly.ai.rag;

public record RagKnowledgeVersion(
    long value,
    long durationMs,
    boolean localHit,
    boolean available
) {
    public static RagKnowledgeVersion unavailable() {
        return new RagKnowledgeVersion(0, 0, false, false);
    }
}
