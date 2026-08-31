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
    void deterministicEmbeddingsReportRecallAtTwoThreeAndFour() throws Exception {
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
        for (int topK : List.of(2, 3, 4)) {
            EvaluationResult result = evaluate(cases, chunks, vectors, provider, topK);
            System.out.printf("Deterministic retrieval Recall@%d: %.3f (%d/%d)%n",
                topK, result.recall(), result.hits(), cases.size());
            if (topK == 3) {
                assertThat(result.misses()).as("Recall@3 queries missing every expected concept")
                    .isEmpty();
                assertThat(result.recall()).isEqualTo(1.0);
            }
        }
    }

    private EvaluationResult evaluate(List<EvaluationCase> cases, List<String> chunks,
                                      List<float[]> vectors, EmbeddingProvider provider, int topK) {
        int hits = 0;
        List<String> misses = new ArrayList<>();
        for (EvaluationCase evaluation : cases) {
            float[] query = provider.embed(evaluation.question());
            List<String> top = java.util.stream.IntStream.range(0, chunks.size()).boxed()
                .sorted(Comparator.comparingDouble((Integer index) -> cosine(query, vectors.get(index)))
                    .reversed())
                .limit(topK).map(chunks::get).toList();
            String retrieved = String.join(" ", top).toLowerCase();
            boolean conceptHit = evaluation.expectedKeyConcepts().stream()
                .map(String::toLowerCase).anyMatch(retrieved::contains);
            if (conceptHit) hits++; else misses.add(evaluation.question());
        }
        return new EvaluationResult(hits, (double) hits / cases.size(), List.copyOf(misses));
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0;
        for (int index = 0; index < left.length; index++) dot += left[index] * right[index];
        return dot;
    }

    private record EvaluationCase(String question, String expectedSource,
                                  List<String> expectedKeyConcepts) { }

    private record EvaluationResult(int hits, double recall, List<String> misses) { }
}
