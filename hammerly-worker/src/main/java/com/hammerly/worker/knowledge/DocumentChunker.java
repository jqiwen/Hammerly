package com.hammerly.worker.knowledge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(KnowledgeWorkerProperties.class)
public class DocumentChunker {
    private final int chunkTokens;
    private final int overlapTokens;

    public DocumentChunker(KnowledgeWorkerProperties properties) {
        this(properties.chunkTokens(), properties.chunkOverlapTokens());
    }

    DocumentChunker(int chunkTokens, int overlapTokens) {
        if (chunkTokens < 1 || overlapTokens < 0 || overlapTokens >= chunkTokens) {
            throw new IllegalArgumentException("Chunk size must exceed a non-negative overlap");
        }
        this.chunkTokens = chunkTokens;
        this.overlapTokens = overlapTokens;
    }

    public List<String> chunk(String content) {
        if (content == null || content.isBlank()) return List.of();
        List<String> words = Arrays.stream(content.strip().split("\\s+"))
            .filter(word -> !word.isBlank()).toList();
        if (words.size() <= chunkTokens) return List.of(String.join(" ", words));

        List<String> chunks = new ArrayList<>();
        int step = chunkTokens - overlapTokens;
        for (int start = 0; start < words.size(); start += step) {
            int end = Math.min(words.size(), start + chunkTokens);
            chunks.add(String.join(" ", words.subList(start, end)));
            if (end == words.size()) break;
        }
        return List.copyOf(chunks);
    }
}
