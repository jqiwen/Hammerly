package com.hammerly.worker.summary;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.worker.config.WorkerProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisConversationSummaryRepositoryTest {
    @Test
    void storesSummaryUnderSeparateKeyWithSevenDayTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        WorkerProperties properties = new WorkerProperties("events", "jobs",
            Duration.ofDays(7), Duration.ofMinutes(2), Duration.ofDays(7));
        RedisConversationSummaryRepository repository = new RedisConversationSummaryRepository(
            redis, new ObjectMapper().findAndRegisterModules(), properties);

        repository.save(new ConversationSummary("42", "conversation-a", 10, "Summary",
            Instant.parse("2026-08-22T12:00:00Z"), UUID.randomUUID()));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("hammerly:conversation:summary:42:conversation-a"),
            json.capture(), eq(Duration.ofDays(7)));
        org.junit.jupiter.api.Assertions.assertTrue(json.getValue().contains("\"summary\":\"Summary\""));
    }
}
