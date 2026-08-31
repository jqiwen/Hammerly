package com.hammerly.ai.service;

import reactor.core.publisher.Flux;

public interface AiModelClient {
    String chat(ModelRequest request);

    Flux<String> stream(ModelRequest request);
}
