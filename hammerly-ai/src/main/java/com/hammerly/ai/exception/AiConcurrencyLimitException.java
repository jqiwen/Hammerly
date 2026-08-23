package com.hammerly.ai.exception;

public class AiConcurrencyLimitException extends AiProviderUnavailableException {
    public static final String MESSAGE =
        "AI service is temporarily busy. Please try again shortly.";

    public AiConcurrencyLimitException(Throwable cause) {
        super("LLM concurrency limit reached.", cause);
    }
}
