package com.hammerly.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeterministicQueryEmbeddingProviderTest {
    @Test
    void createsStableFreeEmbeddingsWithoutProviderCalls() {
        RagProperties properties = new RagProperties(false, 4, 0.2, Duration.ofMinutes(1),
            Duration.ofSeconds(1), "deterministic", "unused", 64,
            "https://example.invalid", "", "", "", "", "disable");
        DeterministicQueryEmbeddingProvider provider =
            new DeterministicQueryEmbeddingProvider(properties);
        assertThat(provider.embed("How do I place a bid?"))
            .containsExactly(provider.embed("How do I place a bid?"))
            .hasSize(64);
    }
}
