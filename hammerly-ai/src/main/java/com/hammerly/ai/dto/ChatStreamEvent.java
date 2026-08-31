package com.hammerly.ai.dto;

import com.hammerly.ai.rag.RagSource;
import java.util.List;

public record ChatStreamEvent(String content, List<RagSource> sources) {
    public ChatStreamEvent(String content) {
        this(content, List.of());
    }

    public static ChatStreamEvent metadata(List<RagSource> sources) {
        return new ChatStreamEvent("", sources);
    }
}
