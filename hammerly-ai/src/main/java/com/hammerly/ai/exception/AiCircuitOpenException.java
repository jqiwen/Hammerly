package com.hammerly.ai.exception;

public class AiCircuitOpenException extends AiProviderUnavailableException {
    public AiCircuitOpenException(Throwable cause) {
        super("LLM provider circuit is open.", cause);
    }
}
