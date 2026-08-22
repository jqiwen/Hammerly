package com.hammerly.ai.cache;

import java.util.Optional;

public interface AiResponseCache {
    Optional<String> get(String key);

    void put(String key, String response);
}
