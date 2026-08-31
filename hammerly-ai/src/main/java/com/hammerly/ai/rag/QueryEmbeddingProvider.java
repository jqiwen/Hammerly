package com.hammerly.ai.rag;

public interface QueryEmbeddingProvider {
    float[] embed(String input);
}
