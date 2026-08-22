package com.hammerly.ai.service;

import com.hammerly.ai.config.AiProviderAvailability;
import com.hammerly.ai.config.HammerlySystemPrompt;
import com.hammerly.ai.dto.ChatMessage;
import com.hammerly.ai.dto.ChatRole;
import com.hammerly.ai.exception.AiProviderUnavailableException;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Component
public class SpringAiModelClient implements AiModelClient {
    private static final String PROVIDER_NOT_CONFIGURED =
        "OpenAI API key is not configured for Hammerly AI.";

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final AiProviderAvailability providerAvailability;
    private final HammerlySystemPrompt systemPrompt;
    private final OpenAiProviderExecutor providerExecutor;
    private volatile ChatClient chatClient;

    public SpringAiModelClient(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                               AiProviderAvailability providerAvailability,
                               HammerlySystemPrompt systemPrompt,
                               OpenAiProviderExecutor providerExecutor) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.providerAvailability = providerAvailability;
        this.systemPrompt = systemPrompt;
        this.providerExecutor = providerExecutor;
    }

    @Override
    public String chat(List<ChatMessage> history, String message) {
        return providerExecutor.execute("chat", () -> {
            String answer = requireChatClient().prompt()
                .messages(toSpringAiMessages(history))
                .user(message)
                .call()
                .content();
            if (!StringUtils.hasText(answer)) {
                throw new AiProviderUnavailableException("AI provider returned an empty response.");
            }
            return answer;
        });
    }

    @Override
    public Flux<String> stream(List<ChatMessage> history, String message) {
        return providerExecutor.stream("stream", () -> requireChatClient().prompt()
                .messages(toSpringAiMessages(history))
                .user(message)
                .stream()
                .content()
                .filter(StringUtils::hasLength)
                .switchIfEmpty(Flux.error(new AiProviderUnavailableException(
                    "AI provider returned an empty response."))));
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
            .map(item -> item.role() == ChatRole.USER
                ? new UserMessage(item.content())
                : new AssistantMessage(item.content()))
            .map(Message.class::cast)
            .toList();
    }
}
