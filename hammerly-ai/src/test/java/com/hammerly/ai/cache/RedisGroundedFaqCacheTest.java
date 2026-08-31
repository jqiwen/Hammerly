package com.hammerly.ai.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.ai.config.HammerlySystemPrompt;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.rag.RagKnowledgeVersion;
import com.hammerly.ai.rag.RagProperties;
import com.hammerly.ai.rag.RagRetrievalService;
import com.hammerly.ai.rag.RagSource;
import com.hammerly.ai.support.AiTestFixtures;
import com.hammerly.ai.support.FakeRedisStateClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedisGroundedFaqCacheTest {
    @Test
    void storesAnswerSourcesVersionModelAndConfiguration() {
        FakeRedisStateClient redis = new FakeRedisStateClient();
        RagRetrievalService rag = mock(RagRetrievalService.class);
        when(rag.localKnowledgeVersion()).thenReturn(new RagKnowledgeVersion(7, 0, true, true));
        RedisGroundedFaqCache cache = cache(redis, rag, "gpt-4.1-mini");
        List<RagSource> sources = List.of(new RagSource("Bidding", "Hammerly Guide"));

        assertThat(cache.lookup("How do I bid?").entry()).isEmpty();
        cache.put("How do I bid?", 7, "Place a higher bid.", sources);
        GroundedFaqCacheEntry entry = cache.lookup("  how   do I BID? ").entry().orElseThrow();

        assertThat(entry.answer()).isEqualTo("Place a higher bid.");
        assertThat(entry.sources()).isEqualTo(sources);
        assertThat(entry.knowledgeBaseVersion()).isEqualTo(7);
        assertThat(entry.model()).isEqualTo("gpt-4.1-mini");

        assertThat(cache(redis, rag, "gpt-4o-mini").lookup("How do I bid?").entry()).isEmpty();
    }

    @Test
    void redisFailureDegradesToMissAndNeverBreaksResponse() {
        FakeRedisStateClient redis = new FakeRedisStateClient();
        redis.failAllOperations();
        RagRetrievalService rag = mock(RagRetrievalService.class);
        when(rag.localKnowledgeVersion()).thenReturn(new RagKnowledgeVersion(7, 0, true, true));
        RedisGroundedFaqCache cache = cache(redis, rag, "gpt-4.1-mini");

        assertThat(cache.lookup("How do I bid?").entry()).isEmpty();
        assertThatCode(() -> cache.put("How do I bid?", 7, "Answer",
            List.of(new RagSource("Bidding", "Guide")))).doesNotThrowAnyException();
    }

    private RedisGroundedFaqCache cache(FakeRedisStateClient redis,
                                         RagRetrievalService rag, String model) {
        return new RedisGroundedFaqCache(redis, new ObjectMapper().findAndRegisterModules(),
            AiTestFixtures.properties(20, 20, Duration.ofMinutes(1)), rag,
            ragProperties(), new HammerlySystemPrompt("system prompt"),
            new AiMetrics(new SimpleMeterRegistry()), model);
    }

    private RagProperties ragProperties() {
        return new RagProperties(true, 3, 0.25, Duration.ofMinutes(5),
            Duration.ofSeconds(45), Duration.ofMillis(1200), "openai",
            "text-embedding-3-small", 1536, "https://api.openai.com", "key",
            "jdbc:postgresql://localhost/test", "", "", "disable");
    }
}
