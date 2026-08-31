package com.hammerly.ai.context;

import com.hammerly.ai.dto.ChatMessage;
import com.hammerly.ai.rag.RagSource;
import java.util.List;

public record BuiltAiContext(
    List<ChatMessage> messages,
    String question,
    String systemContext,
    List<RagSource> sources,
    long knowledgeBaseVersion,
    long contextDurationMs,
    long summaryDurationMs,
    long ragDurationMs,
    long knowledgeVersionDurationMs,
    long ragCacheDurationMs,
    long embeddingDurationMs,
    long ragSearchDurationMs
) {
    public BuiltAiContext {
        messages = List.copyOf(messages);
        sources = List.copyOf(sources);
    }

    public BuiltAiContext(List<ChatMessage> messages, String question, String systemContext,
                          List<RagSource> sources) {
        this(messages, question, systemContext, sources, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
