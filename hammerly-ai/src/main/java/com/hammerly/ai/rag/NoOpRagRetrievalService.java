package com.hammerly.ai.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "hammerly.ai.rag", name = "enabled",
    havingValue = "false", matchIfMissing = true)
public class NoOpRagRetrievalService implements RagRetrievalService {
    @Override
    public RagResult retrieve(String query) {
        return RagResult.empty();
    }
}
