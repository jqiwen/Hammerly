package com.hammerly.ai.ratelimit;

public record RateLimitDecision(
    boolean allowed,
    int limit,
    int remaining,
    long resetEpochSeconds,
    boolean redisAvailable
) {
    public static RateLimitDecision prechecked() {
        return new RateLimitDecision(true, 0, 0, 0, true);
    }
}
