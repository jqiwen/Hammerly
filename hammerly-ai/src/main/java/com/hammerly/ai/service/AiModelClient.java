package com.hammerly.ai.service;

import com.hammerly.ai.dto.ChatMessage;
import java.util.List;
import reactor.core.publisher.Flux;

public interface AiModelClient {
    String chat(List<ChatMessage> history, String message);

    Flux<String> stream(List<ChatMessage> history, String message);
}
