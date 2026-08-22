package com.hammerly.ai.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.ai.config.AiStateProperties;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.redis.RedisStateClient;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RedisConversationStore implements ConversationStore {
    private static final Logger log = LoggerFactory.getLogger(RedisConversationStore.class);
    private static final String KEY_PREFIX = "hammerly:conversation:";

    private final RedisStateClient redis;
    private final ObjectMapper objectMapper;
    private final AiStateProperties properties;
    private final AiMetrics metrics;

    public RedisConversationStore(RedisStateClient redis, ObjectMapper objectMapper,
                                  AiStateProperties properties, AiMetrics metrics) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public ConversationHistory getRecent(String userId, String conversationId) {
        String key = key(userId, conversationId);
        try {
            List<ConversationMessage> messages = new ArrayList<>();
            for (String json : redis.listRange(key)) {
                messages.add(objectMapper.readValue(json, ConversationMessage.class));
            }
            metrics.conversationRead(true);
            return ConversationHistory.available(messages);
        } catch (RuntimeException | JsonProcessingException exception) {
            metrics.conversationRead(false);
            metrics.redisError("conversation_read");
            log.warn("Redis conversation read failed; continuing without stored context key={} errorType={}",
                key, rootCauseName(exception));
            return ConversationHistory.unavailable();
        }
    }

    @Override
    public void append(String userId, String conversationId, List<ConversationMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }
        String key = key(userId, conversationId);
        try {
            List<String> values = messages.stream().map(this::serialize).toList();
            redis.appendAndTrim(key, values, properties.conversation().maxMessages(),
                properties.conversation().ttl());
            metrics.conversationWrite(true);
        } catch (RuntimeException exception) {
            metrics.conversationWrite(false);
            metrics.redisError("conversation_write");
            log.warn("Redis conversation write failed; response will still be returned key={} errorType={}",
                key, rootCauseName(exception));
        }
    }

    static String key(String userId, String conversationId) {
        return KEY_PREFIX + userId + ":" + conversationId;
    }

    private String serialize(ConversationMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize conversation message", exception);
        }
    }

    private String rootCauseName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }
}
