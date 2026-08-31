package com.hammerly.worker.knowledge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(KnowledgeWorkerProperties.class)
public class DocumentChunker {
    private final int chunkTokens;
    private final int overlapTokens;

    @Autowired
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

    /**
     * Keeps Markdown H2 sections independent so retrieval can cite a meaningful section such as
     * "Bidding" instead of embedding an entire support manual as one broad chunk.
     */
    public List<String> chunk(String content) {
        if (content == null || content.isBlank()) return List.of();
        List<String> chunks = new ArrayList<>();
        for (MarkdownSection section : markdownSections(content)) {
            appendSectionChunks(section, chunks);
        }
        return List.copyOf(chunks);
    }

    private void appendSectionChunks(MarkdownSection section, List<String> target) {
        List<String> words = Arrays.stream(section.body().strip().split("\\s+"))
            .filter(word -> !word.isBlank()).toList();
        int headingTokens = section.heading().isBlank()
            ? 0 : section.heading().split("\\s+").length;
        int bodyCapacity = Math.max(1, chunkTokens - headingTokens);
        if (words.isEmpty()) {
            if (!section.heading().isBlank()) target.add(section.heading());
            return;
        }

        int overlap = Math.min(overlapTokens, Math.max(0, bodyCapacity - 1));
        int step = Math.max(1, bodyCapacity - overlap);
        for (int start = 0; start < words.size(); start += step) {
            int end = Math.min(words.size(), start + bodyCapacity);
            String body = String.join(" ", words.subList(start, end));
            target.add(section.heading().isBlank() ? body : section.heading() + "\n" + body);
            if (end == words.size()) break;
        }
    }

    private List<MarkdownSection> markdownSections(String content) {
        List<MarkdownSection> sections = new ArrayList<>();
        String heading = "";
        StringBuilder body = new StringBuilder();
        for (String line : content.strip().split("\\R")) {
            if (line.startsWith("## ")) {
                addSection(sections, heading, body);
                heading = line.strip();
                body = new StringBuilder();
            } else if (heading.isBlank() && body.isEmpty() && line.startsWith("# ")) {
                heading = line.strip();
            } else {
                if (!body.isEmpty()) body.append('\n');
                body.append(line);
            }
        }
        addSection(sections, heading, body);
        return sections;
    }

    private void addSection(List<MarkdownSection> sections, String heading, StringBuilder body) {
        if (!heading.isBlank() || !body.toString().isBlank()) {
            sections.add(new MarkdownSection(heading, body.toString()));
        }
    }

    private record MarkdownSection(String heading, String body) {
    }
}
