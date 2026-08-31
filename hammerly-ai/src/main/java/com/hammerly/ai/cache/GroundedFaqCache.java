package com.hammerly.ai.cache;

import com.hammerly.ai.rag.RagSource;
import java.util.List;

public interface GroundedFaqCache {
    GroundedFaqCacheProbe lookup(String question);

    void put(String question, long knowledgeBaseVersion, String answer, List<RagSource> sources);

    static GroundedFaqCache disabled() {
        return new GroundedFaqCache() {
            @Override
            public GroundedFaqCacheProbe lookup(String question) {
                return GroundedFaqCacheProbe.unavailable();
            }

            @Override
            public void put(String question, long knowledgeBaseVersion, String answer,
                            List<RagSource> sources) {
            }
        };
    }
}
