package com.hammerly.ai.rag;

public interface RagRetrievalService {
    RagResult retrieve(String query);

    default RagKnowledgeVersion knowledgeVersion() {
        return RagKnowledgeVersion.unavailable();
    }

    default RagKnowledgeVersion localKnowledgeVersion() {
        return RagKnowledgeVersion.unavailable();
    }
}
