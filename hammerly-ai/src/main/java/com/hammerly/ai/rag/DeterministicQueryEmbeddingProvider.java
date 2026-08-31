package com.hammerly.ai.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "hammerly.ai.rag", name = "embedding-provider",
    havingValue = "deterministic", matchIfMissing = true)
public class DeterministicQueryEmbeddingProvider implements QueryEmbeddingProvider {
    private final int dimension;

    public DeterministicQueryEmbeddingProvider(RagProperties properties) {
        this.dimension = properties.embeddingDimension();
    }

    @Override
    public float[] embed(String input) {
        float[] vector = new float[dimension];
        for (String token : input.toLowerCase().split("[^a-z0-9]+")) {
            if (token.isBlank()) continue;
            byte[] hash = sha256(token);
            int index = Math.floorMod(((hash[0] & 0xff) << 24) | ((hash[1] & 0xff) << 16)
                | ((hash[2] & 0xff) << 8) | (hash[3] & 0xff), dimension);
            vector[index] += (hash[4] & 1) == 0 ? 1f : -1f;
        }
        double sum = 0;
        for (float value : vector) sum += value * value;
        if (sum > 0) {
            float norm = (float) Math.sqrt(sum);
            for (int index = 0; index < vector.length; index++) vector[index] /= norm;
        }
        return vector;
    }

    private byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
