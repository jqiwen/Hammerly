package com.hammerly.worker.summary;

import com.hammerly.worker.event.ConversationSummaryRequestedPayload;
import com.hammerly.worker.event.EventEnvelope;
import com.hammerly.worker.observability.WorkerMetrics;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class ConversationSummaryHandler {
    private final ConversationSummarizer summarizer;
    private final ConversationSummaryRepository repository;
    private final WorkerMetrics metrics;
    private final Clock clock;

    public ConversationSummaryHandler(ConversationSummarizer summarizer,
                                      ConversationSummaryRepository repository,
                                      WorkerMetrics metrics, Clock clock) {
        this.summarizer = summarizer;
        this.repository = repository;
        this.metrics = metrics;
        this.clock = clock;
    }

    public void handle(EventEnvelope event, ConversationSummaryRequestedPayload payload) {
        long startedAt = System.nanoTime();
        try {
            String summaryText = summarizer.summarize(payload);
            repository.save(new ConversationSummary(event.userId(), event.conversationId(),
                payload.messageCount(), summaryText, clock.instant(), event.eventId()));
            metrics.summarySuccess(startedAt);
        } catch (RuntimeException exception) {
            metrics.summaryFailure(startedAt);
            throw exception;
        }
    }
}
