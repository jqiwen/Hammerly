package com.hammerly.ai.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.ai.dto.ChatRole;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.support.AiTestFixtures;
import com.hammerly.ai.support.FakeRedisStateClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisConversationStoreTest {
    private FakeRedisStateClient redis;
    private RedisConversationStore store;

    @BeforeEach
    void setUp() {
        redis = new FakeRedisStateClient();
        store = new RedisConversationStore(redis, new ObjectMapper().findAndRegisterModules(),
            AiTestFixtures.properties(3, 20, Duration.ofMinutes(1)),
            new AiMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void appendsReturnsInOrderAndCapsHistory() {
        store.append("1", "conversation-a", List.of(
            message(ChatRole.USER, "one"),
            message(ChatRole.ASSISTANT, "two")
        ));
        store.append("1", "conversation-a", List.of(
            message(ChatRole.USER, "three"),
            message(ChatRole.ASSISTANT, "four")
        ));

        List<String> contents = store.getRecent("1", "conversation-a").messages().stream()
            .map(ConversationMessage::content)
            .toList();

        assertEquals(List.of("two", "three", "four"), contents);
    }

    @Test
    void isolatesUsersAndConversations() {
        store.append("1", "conversation-a", List.of(message(ChatRole.USER, "user one")));
        store.append("2", "conversation-a", List.of(message(ChatRole.USER, "user two")));
        store.append("1", "conversation-b", List.of(message(ChatRole.USER, "other chat")));

        assertEquals("user one", store.getRecent("1", "conversation-a").messages().getFirst().content());
        assertEquals("user two", store.getRecent("2", "conversation-a").messages().getFirst().content());
        assertEquals("other chat", store.getRecent("1", "conversation-b").messages().getFirst().content());
    }

    @Test
    void redisFailureReturnsUnavailableHistoryAndDoesNotThrowOnWrite() {
        redis.failAllOperations();

        ConversationHistory history = store.getRecent("1", "conversation-a");
        store.append("1", "conversation-a", List.of(message(ChatRole.USER, "still safe")));

        assertFalse(history.redisAvailable());
        assertTrue(history.messages().isEmpty());
    }

    private ConversationMessage message(ChatRole role, String content) {
        return new ConversationMessage(role, content, Instant.parse("2026-08-22T12:00:00Z"));
    }
}
