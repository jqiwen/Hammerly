package com.hammerly.ai.service;

import com.hammerly.ai.ratelimit.RateLimitDecision;

public record AiChatResult(String answer, RateLimitDecision rateLimit) {
}
