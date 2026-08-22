package com.hammerly.worker.summary;

import com.hammerly.worker.event.ConversationSummaryRequestedPayload;

public interface ConversationSummarizer {
    String summarize(ConversationSummaryRequestedPayload conversation);
}
