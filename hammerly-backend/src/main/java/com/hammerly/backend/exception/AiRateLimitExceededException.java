package com.hammerly.backend.exception;

import com.hammerly.backend.client.AiRateLimitStatus;

public class AiRateLimitExceededException extends RuntimeException {
    public static final String MESSAGE = "Too many AI requests. Please try again shortly.";

    private final AiRateLimitStatus rateLimit;

    public AiRateLimitExceededException(AiRateLimitStatus rateLimit) {
        super(MESSAGE);
        this.rateLimit = rateLimit;
    }

    public AiRateLimitStatus rateLimit() {
        return rateLimit;
    }
}
