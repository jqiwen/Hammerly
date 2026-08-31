package com.hammerly.backend.client;

public final class InternalAiHeaders {
    public static final String USER_ID = "X-Hammerly-User-Id";
    public static final String RATE_LIMIT_PRECHECKED = "X-Hammerly-Rate-Limit-Prechecked";
    public static final String INTERNAL_TOKEN = "X-Hammerly-Internal-Token";
    public static final String CORE_AI_STARTED_AT = "X-Hammerly-Core-Ai-Started-At";

    private InternalAiHeaders() {
    }
}
