package com.hammerly.ai.diagnostic;

import com.openai.models.completions.CompletionUsage;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Logs provider completion metadata without logging prompts, retrieved content, or user text. */
@Component
public class OpenAiCompletionDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompletionDiagnostics.class);
    private final String configuredModel;

    public OpenAiCompletionDiagnostics(
            @Value("${spring.ai.openai-sdk.chat.options.model:unknown}") String configuredModel) {
        this.configuredModel = configuredModel;
    }

    public Tracker start(String operation) {
        return new Tracker(operation);
    }

    public final class Tracker {
        private final String operation;
        private final AtomicBoolean logged = new AtomicBoolean();
        private String model = configuredModel;
        private String finishReason = "unknown";
        private int outputTokens = -1;
        private long reasoningTokens = -1;
        private int visibleCharacters;

        private Tracker(String operation) {
            this.operation = operation;
        }

        public void observe(ChatResponse response) {
            if (response == null) return;
            if (response.getMetadata() != null) {
                if (response.getMetadata().getModel() != null
                        && !response.getMetadata().getModel().isBlank()) {
                    model = response.getMetadata().getModel();
                }
                observeUsage(response.getMetadata().getUsage());
            }
            if (response.getResult() != null && response.getResult().getMetadata() != null) {
                String value = response.getResult().getMetadata().getFinishReason();
                if (value != null && !value.isBlank()) finishReason = value;
            }
        }

        public void visibleCharacters(int characters) {
            visibleCharacters += Math.max(0, characters);
        }

        public void complete() {
            if (!logged.compareAndSet(false, true)) return;
            if (isLengthLimited(finishReason)) {
                log.warn("openai_completion_truncated operation={} model={} finishReason={} "
                        + "outputTokens={} reasoningTokens={} visibleCharacters={}",
                    operation, model, finishReason, outputTokens, reasoningTokens,
                    visibleCharacters);
            } else {
                log.info("openai_completion operation={} model={} finishReason={} outputTokens={} "
                        + "reasoningTokens={} visibleCharacters={}",
                    operation, model, finishReason, outputTokens, reasoningTokens,
                    visibleCharacters);
            }
        }

        private void observeUsage(Usage usage) {
            if (usage == null) return;
            if (usage.getCompletionTokens() != null) outputTokens = usage.getCompletionTokens();
            if (usage.getNativeUsage() instanceof CompletionUsage nativeUsage) {
                reasoningTokens = nativeUsage.completionTokensDetails()
                    .flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
                    .orElse(-1L);
            }
        }
    }

    static boolean isLengthLimited(String finishReason) {
        if (finishReason == null) return false;
        String normalized = finishReason.toLowerCase(Locale.ROOT);
        return normalized.contains("length") || normalized.contains("max_token");
    }
}
