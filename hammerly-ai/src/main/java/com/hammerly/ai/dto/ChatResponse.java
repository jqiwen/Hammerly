package com.hammerly.ai.dto;

import com.hammerly.ai.rag.RagSource;
import java.util.List;

public record ChatResponse(String answer, List<RagSource> sources) {
    public ChatResponse(String answer) {
        this(answer, List.of());
    }
}
