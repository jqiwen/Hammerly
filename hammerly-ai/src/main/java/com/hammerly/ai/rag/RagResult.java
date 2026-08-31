package com.hammerly.ai.rag;

import java.util.List;

public record RagResult(List<RagChunk> chunks, long knowledgeVersion) {
    public RagResult {
        chunks = List.copyOf(chunks);
    }

    public static RagResult empty() {
        return new RagResult(List.of(), 0);
    }
}
