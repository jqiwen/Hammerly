package com.hammerly.worker.summary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.worker.config.WorkerProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisConversationSummaryRepository implements ConversationSummaryRepository {
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final WorkerProperties properties;

    public RedisConversationSummaryRepository(StringRedisTemplate redis, ObjectMapper objectMapper,
                                              WorkerProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void save(ConversationSummary summary) {
        try {
            redis.opsForValue().set(key(summary.userId(), summary.conversationId()),
                objectMapper.writeValueAsString(summary), properties.summaryTtl());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize conversation summary", exception);
        }
    }

    public static String key(String userId, String conversationId) {
        return "hammerly:conversation:summary:" + userId + ":" + conversationId;
    }
}
