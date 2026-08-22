package com.hammerly.ai.exception;

import com.hammerly.ai.ratelimit.RateLimitDecision;

public class AiRateLimitExceededException extends RuntimeException {
    public static final String MESSAGE = "Too many AI requests. Please try again shortly.";

    private final RateLimitDecision decision;

    public AiRateLimitExceededException(RateLimitDecision decision) {
        super(MESSAGE);
        this.decision = decision;
    }

    public RateLimitDecision decision() {
        return decision;
    }
}
