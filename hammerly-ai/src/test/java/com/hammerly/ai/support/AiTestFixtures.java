package com.hammerly.ai.support;

import com.hammerly.ai.config.AiStateProperties;
import java.time.Duration;

public final class AiTestFixtures {
    private AiTestFixtures() {
    }

    public static AiStateProperties properties(int maxMessages, int requests, Duration window) {
        return new AiStateProperties(
            new AiStateProperties.Conversation(maxMessages, Duration.ofHours(24)),
            new AiStateProperties.ResponseCache(Duration.ofMinutes(15)),
            new AiStateProperties.RateLimit(requests, window)
        );
    }
}
