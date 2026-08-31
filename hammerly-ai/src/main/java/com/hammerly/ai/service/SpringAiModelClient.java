package com.hammerly.ai.service;

import com.hammerly.ai.config.AiProviderAvailability;
import com.hammerly.ai.config.HammerlySystemPrompt;
import com.hammerly.ai.diagnostic.OpenAiCompletionDiagnostics;
import com.hammerly.ai.dto.ChatMessage;
import com.hammerly.ai.dto.ChatRole;
import com.hammerly.ai.exception.AiProviderUnavailableException;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Component
@Profile("!loadtest")
public class SpringAiModelClient implements AiModelClient {
    private static final String PROVIDER_NOT_CONFIGURED =
        "OpenAI API key is not configured for Hammerly AI.";

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final AiProviderAvailability providerAvailability;
    private final HammerlySystemPrompt systemPrompt;
    private final OpenAiProviderExecutor providerExecutor;
    private final OpenAiCompletionDiagnostics completionDiagnostics;
    private volatile ChatClient chatClient;

    public SpringAiModelClient(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                               AiProviderAvailability providerAvailability,
                               HammerlySystemPrompt systemPrompt,
                               OpenAiProviderExecutor providerExecutor,
                               OpenAiCompletionDiagnostics completionDiagnostics) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.providerAvailability = providerAvailability;
        this.systemPrompt = systemPrompt;
        this.providerExecutor = providerExecutor;
        this.completionDiagnostics = completionDiagnostics;
    }

    @Override
    public String chat(ModelRequest request) {
        return providerExecutor.execute("chat", () -> {
            OpenAiCompletionDiagnostics.Tracker diagnostics = completionDiagnostics.start("chat");
            ChatResponse response = prompt(request).call().chatResponse();
            diagnostics.observe(response);
            String answer = responseContent(response);
            diagnostics.visibleCharacters(answer.length());
            diagnostics.complete();
            if (!StringUtils.hasText(answer)) {
                throw new AiProviderUnavailableException("AI provider returned an empty response.");
            }
            return answer;
        });
    }

    @Override
    public Flux<String> stream(ModelRequest request) {
        return providerExecutor.stream("stream", () -> {
            OpenAiCompletionDiagnostics.Tracker diagnostics = completionDiagnostics.start("stream");
            return prompt(request).stream().chatResponse()
                .doOnNext(diagnostics::observe)
                .map(this::responseContent)
                .filter(StringUtils::hasLength)
                .doOnNext(chunk -> diagnostics.visibleCharacters(chunk.length()))
                .doOnComplete(diagnostics::complete)
                .switchIfEmpty(Flux.error(new AiProviderUnavailableException(
                    "AI provider returned an empty response.")));
        });
    }

    private ChatClient.ChatClientRequestSpec prompt(ModelRequest request) {
        return requireChatClient().prompt()
            .system(combinedSystemPrompt(request.systemContext()))
            .messages(toSpringAiMessages(request.history()))
            .user(request.question());
    }

    private String combinedSystemPrompt(String contextualReference) {
        if (!StringUtils.hasText(contextualReference)) return systemPrompt.content();
        return systemPrompt.content() + "\n\n" + contextualReference;
    }

    private String responseContent(ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) return "";
        return response.getResult().getOutput().getText();
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
                    current = builder.build();
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
