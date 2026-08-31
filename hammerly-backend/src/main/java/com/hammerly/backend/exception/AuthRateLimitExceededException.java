package com.hammerly.backend.exception;

public class AuthRateLimitExceededException extends RuntimeException {
    public static final String MESSAGE = "Too many authentication attempts. Please try again later.";
    private final int limit;
    private final long retryAfterSeconds;

    public AuthRateLimitExceededException(int limit, long retryAfterSeconds) {
        super(MESSAGE);
        this.limit = limit;
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public int limit() {
        return limit;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
