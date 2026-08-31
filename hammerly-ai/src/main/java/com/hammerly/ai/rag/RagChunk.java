package com.hammerly.ai.rag;

public record RagChunk(
    String chunkId,
    String title,
    String source,
    String content,
    double similarity
) {
    public RagSource citation() {
        return new RagSource(title, source, chunkId);
    }
}
