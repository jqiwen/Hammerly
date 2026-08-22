package com.hammerly.worker.summary;

import com.hammerly.worker.event.ConversationSummaryRequestedPayload;
import com.hammerly.worker.event.SummaryMessage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ExtractiveConversationSummarizer implements ConversationSummarizer {
    private static final int MAX_SUMMARY_LENGTH = 1_200;
    private static final int MAX_INCLUDED_MESSAGES = 8;

    @Override
    public String summarize(ConversationSummaryRequestedPayload conversation) {
        List<SummaryMessage> meaningful = conversation.messages().stream()
            .filter(message -> message != null && StringUtils.hasText(message.content()))
            .toList();
        if (meaningful.isEmpty()) {
            throw new IllegalArgumentException("Summary request has no message content");
        }

        int start = Math.max(0, meaningful.size() - MAX_INCLUDED_MESSAGES);
        List<String> excerpts = new ArrayList<>();
        for (SummaryMessage message : meaningful.subList(start, meaningful.size())) {
            String speaker = "USER".equalsIgnoreCase(message.role()) ? "User" : "Assistant";
            excerpts.add(speaker + ": " + compact(message.content()));
        }
        String summary = String.join(" ", excerpts);
        return summary.length() <= MAX_SUMMARY_LENGTH
            ? summary
            : summary.substring(0, MAX_SUMMARY_LENGTH - 1).stripTrailing() + "…";
    }

    private String compact(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }
}
