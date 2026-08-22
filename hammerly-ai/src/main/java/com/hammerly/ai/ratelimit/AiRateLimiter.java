package com.hammerly.ai.ratelimit;

public interface AiRateLimiter {
    RateLimitDecision acquire(String userId);
}
