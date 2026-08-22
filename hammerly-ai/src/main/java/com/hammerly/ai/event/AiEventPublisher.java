package com.hammerly.ai.event;

public interface AiEventPublisher {
    void publishSuccessfulTurn(SuccessfulAiTurn turn);
}
