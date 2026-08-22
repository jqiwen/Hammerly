package com.hammerly.backend.client;

public record AiPlatformResponse<T>(T body, AiRateLimitStatus rateLimit) {
}
