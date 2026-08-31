package com.hammerly.worker.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagRetrievalEvaluationTest {
    @Test
    void deterministicEmbeddingsRetrieveExpectedConceptAtTopFour() throws Exception {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        String document = Files.readString(repository.resolve(
            "docs/knowledge-base/hammerly-support.md"));
        List<EvaluationCase> cases = new ObjectMapper().readValue(Files.readString(
            repository.resolve("docs/rag/evaluation.json")), new TypeReference<>() { });

        DocumentChunker chunker = new DocumentChunker(120, 20);
        DeterministicEmbeddingProvider provider = new DeterministicEmbeddingProvider(
            new KnowledgeWorkerProperties(120, 20, "deterministic", "deterministic-v1", 1536,
                "https://api.openai.com", "", Duration.ofSeconds(2)));
        List<String> chunks = chunker.chunk(document);
        List<float[]> vectors = chunks.stream().map(provider::embed).toList();
        int hits = 0;
        List<String> misses = new ArrayList<>();

        for (EvaluationCase evaluation : cases) {
            float[] query = provider.embed(evaluation.question());
            List<String> top = java.util.stream.IntStream.range(0, chunks.size()).boxed()
                .sorted(Comparator.comparingDouble((Integer index) -> cosine(query, vectors.get(index)))
                    .reversed())
                .limit(4).map(chunks::get).toList();
            String retrieved = String.join(" ", top).toLowerCase();
            boolean conceptHit = evaluation.expectedKeyConcepts().stream()
                .map(String::toLowerCase).anyMatch(retrieved::contains);
            if (conceptHit) hits++; else misses.add(evaluation.question());
        }

        double recallAtFour = (double) hits / cases.size();
        System.out.printf("Deterministic retrieval Recall@4: %.3f (%d/%d)%n",
            recallAtFour, hits, cases.size());
        assertThat(misses).as("queries missing every expected concept").isEmpty();
        assertThat(recallAtFour).isEqualTo(1.0);
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0;
        for (int index = 0; index < left.length; index++) dot += left[index] * right[index];
        return dot;
    }

    private record EvaluationCase(String question, String expectedSource,
                                  List<String> expectedKeyConcepts) { }
}
