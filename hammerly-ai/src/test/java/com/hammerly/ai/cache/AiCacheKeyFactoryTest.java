package com.hammerly.ai.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hammerly.ai.config.HammerlySystemPrompt;
import com.hammerly.ai.dto.ChatMessage;
import com.hammerly.ai.dto.ChatRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiCacheKeyFactoryTest {
    private final AiCacheKeyFactory factory =
        new AiCacheKeyFactory(new HammerlySystemPrompt("system prompt v1"));

    @Test
    void keyIsStableForEquivalentNormalizedPrompt() {
        String first = factory.create("1", "conversation", " How   do I bid? ", List.of());
        String second = factory.create("1", "conversation", "How do I bid?", List.of());

        assertEquals(first, second);
        assertTrue(first.matches("hammerly:ai:response:v1:[0-9a-f]{64}"));
    }

    @Test
    void contextUserAndConversationAllAffectKey() {
        List<ChatMessage> firstContext = List.of(new ChatMessage(ChatRole.USER, "first context"));
        List<ChatMessage> secondContext = List.of(new ChatMessage(ChatRole.USER, "other context"));
        String baseline = factory.create("1", "conversation-a", "Help", firstContext);

        assertNotEquals(baseline, factory.create("1", "conversation-a", "Help", secondContext));
        assertNotEquals(baseline, factory.create("2", "conversation-a", "Help", firstContext));
        assertNotEquals(baseline, factory.create("1", "conversation-b", "Help", firstContext));
    }
}
