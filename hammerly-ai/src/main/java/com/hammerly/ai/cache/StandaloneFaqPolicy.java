package com.hammerly.ai.cache;

import com.hammerly.ai.dto.ChatRequest;
import org.springframework.stereotype.Component;

@Component
public class StandaloneFaqPolicy {
    public boolean allowsFastCache(ChatRequest request) {
        return request.standaloneFaq() && request.history().isEmpty();
    }
}
