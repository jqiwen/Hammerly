package com.hammerly.worker.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {
    private final DocumentChunker chunker = new DocumentChunker(10, 3);

    @Test
    void emptyInputCreatesNoChunks() {
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("   \n ")).isEmpty();
    }

    @Test
    void smallDocumentIsNotSplit() {
        assertThat(chunker.chunk("one two three")).containsExactly("one two three");
    }

    @Test
    void largeDocumentHasStableOrderingAndBoundedChunks() {
        String input = String.join(" ", IntStream.range(0, 25).mapToObj(i -> "word" + i).toList());
        List<String> first = chunker.chunk(input);
        assertThat(first).isEqualTo(chunker.chunk(input)).hasSize(4);
        assertThat(first).allSatisfy(chunk ->
            assertThat(chunk.split(" ").length).isLessThanOrEqualTo(10));
    }

    @Test
    void adjacentChunksPreserveConfiguredOverlap() {
        String input = String.join(" ", IntStream.range(0, 18).mapToObj(i -> "w" + i).toList());
        List<String> chunks = chunker.chunk(input);
        assertThat(chunks.get(0).split(" ")).endsWith("w7", "w8", "w9");
        assertThat(chunks.get(1).split(" ")).startsWith("w7", "w8", "w9");
        assertThat(chunks).noneMatch(String::isBlank);
    }

    @Test
    void markdownSectionsProduceIndependentCitableChunks() {
        String document = """
            # Hammerly Support Guide
            Introductory support reference.

            ## Bidding
            Sign in and bid above the current amount.

            ## Watchlists
            Watchlists are personal bookmarks.
            """;
        DocumentChunker sectionChunker = new DocumentChunker(40, 5);

        List<String> chunks = sectionChunker.chunk(document);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).anySatisfy(chunk ->
            assertThat(chunk).startsWith("## Bidding\n").contains("bid above"));
        assertThat(chunks).anySatisfy(chunk ->
            assertThat(chunk).startsWith("## Watchlists\n").contains("bookmarks"));
    }
}
