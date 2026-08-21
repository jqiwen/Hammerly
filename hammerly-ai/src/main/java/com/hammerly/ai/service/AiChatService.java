package com.hammerly.ai.service;

import com.hammerly.ai.config.AiProviderAvailability;
import com.hammerly.ai.config.HammerlySystemPrompt;
import com.hammerly.ai.dto.ChatMessage;
import com.hammerly.ai.dto.ChatRequest;
import com.hammerly.ai.dto.ChatRole;
import com.hammerly.ai.exception.AiProviderUnavailableException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
public class AiChatService {
    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
    private static final String PROVIDER_NOT_CONFIGURED =
        "OPENAI_API_KEY is not configured for Hammerly AI.";

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final AiProviderAvailability providerAvailability;
    private final HammerlySystemPrompt systemPrompt;
    private volatile ChatClient chatClient;

    public AiChatService(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                         AiProviderAvailability providerAvailability,
                         HammerlySystemPrompt systemPrompt) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.providerAvailability = providerAvailability;
        this.systemPrompt = systemPrompt;
    }

    public String chat(ChatRequest request) {
        long startedAt = System.nanoTime();
        log.info("AI chat request started historyMessages={}", request.history().size());
        try {
            String answer = requireChatClient().prompt()
                .messages(toSpringAiMessages(request.history()))
                .user(request.message())
                .call()
                .content();
            if (!StringUtils.hasText(answer)) {
                throw new AiProviderUnavailableException("AI provider returned an empty response.");
            }
            log.info("AI chat request completed durationMs={}", elapsedMillis(startedAt));
            return answer;
        } catch (AiProviderUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("AI chat request failed durationMs={} errorType={}",
                elapsedMillis(startedAt), rootCauseName(exception));
            throw new AiProviderUnavailableException("AI provider request failed.", exception);
        }
    }

    public Flux<String> stream(ChatRequest request) {
        long startedAt = System.nanoTime();
        log.info("AI stream started historyMessages={}", request.history().size());

        final Flux<String> chunks;
        try {
            chunks = requireChatClient().prompt()
                .messages(toSpringAiMessages(request.history()))
                .user(request.message())
                .stream()
                .content();
        } catch (AiProviderUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiProviderUnavailableException("AI provider stream could not start.", exception);
        }

        return chunks
            .filter(StringUtils::hasLength)
            .doOnComplete(() -> log.info("AI stream completed durationMs={}", elapsedMillis(startedAt)))
            .doOnError(exception -> log.warn("AI stream failed durationMs={} errorType={}",
                elapsedMillis(startedAt), rootCauseName(exception)))
            .onErrorMap(exception -> exception instanceof AiProviderUnavailableException
                ? exception
                : new AiProviderUnavailableException("AI provider stream was interrupted.", exception));
    }

    private ChatClient requireChatClient() {
        if (!providerAvailability.isConfigured()) {
            throw new AiProviderUnavailableException(PROVIDER_NOT_CONFIGURED);
        }

        ChatClient current = chatClient;
        if (current == null) {
            synchronized (this) {
                current = chatClient;
                if (current == null) {
                    ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
                    if (builder == null) {
                        throw new AiProviderUnavailableException("OpenAI chat model is unavailable.");
                    }
                    current = builder.defaultSystem(systemPrompt.content()).build();
                    chatClient = current;
                }
            }
        }
        return current;
    }

    private List<Message> toSpringAiMessages(List<ChatMessage> history) {
        return history.stream()
            .map(message -> message.role() == ChatRole.USER
                ? new UserMessage(message.content())
                : new AssistantMessage(message.content()))
            .map(Message.class::cast)
            .toList();
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String rootCauseName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }
}
