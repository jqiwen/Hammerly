package com.hammerly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hammerly.ai.redis.InMemoryRedisStateClient;
import com.hammerly.ai.redis.RedisStateClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "hammerly.redis.enabled=false",
    "hammerly.kafka.enabled=false",
    "spring.data.redis.host=redis-must-not-be-contacted.invalid",
    "hammerly.ai.loadtest-provider.first-token-delay=0ms",
    "hammerly.ai.loadtest-provider.token-interval=0ms",
    "hammerly.ai.loadtest-provider.token-count=2"
})
@ActiveProfiles("loadtest")
@AutoConfigureMockMvc
class RedisDisabledChatApplicationTest {
    private static final String CONVERSATION_ID = "27d6ac91-3f38-4a33-91e9-fde61f99d8c2";

    @Autowired
    MockMvc mvc;

    @Autowired
    RedisStateClient state;

    @Test
    void chatCompletesAndStoresProcessLocalHistoryWithoutRedis() throws Exception {
        assertInstanceOf(InMemoryRedisStateClient.class, state);

        mvc.perform(post("/internal/ai/chat")
                .header("X-Hammerly-User-Id", "redis-off-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"message":"Can I still chat?","history":[],"conversationId":"%s"}
                    """.formatted(CONVERSATION_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Hammerly token-1 "));

        assertEquals(2, state.listRange(
            "hammerly:conversation:redis-off-test:" + CONVERSATION_ID).size());
    }
}
