package com.hammerly.backend.client;

public record AiRateLimitStatus(int limit, int remaining, long resetEpochSeconds) {
}
