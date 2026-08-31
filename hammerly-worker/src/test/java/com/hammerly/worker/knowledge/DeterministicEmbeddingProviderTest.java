package com.hammerly.worker.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeterministicEmbeddingProviderTest {
    private final DeterministicEmbeddingProvider provider = new DeterministicEmbeddingProvider(
        new KnowledgeWorkerProperties(650, 100, "deterministic", "unused", 32,
            "https://example.invalid", "", Duration.ofSeconds(1)));

    @Test
    void embeddingsAreStableSizedAndNormalized() {
        float[] first = provider.embed("Place a bid on an active auction");
        float[] second = provider.embed("Place a bid on an active auction");
        assertThat(first).containsExactly(second).hasSize(32);
        double magnitude = Math.sqrt(java.util.stream.IntStream.range(0, first.length)
            .mapToDouble(i -> first[i] * first[i]).sum());
        assertThat(magnitude).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void differentTextProducesDifferentEmbedding() {
        assertThat(provider.embed("bidding auctions"))
            .isNotEqualTo(provider.embed("profile avatar"));
    }
}
