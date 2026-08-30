package com.hammerly.worker.knowledge;

public interface EmbeddingProvider {
    float[] embed(String content);
}
