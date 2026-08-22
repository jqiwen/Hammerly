package com.hammerly.ai.diagnostic;

public record OpenAiProviderFailure(
    Category category,
    Integer status,
    String code,
    String exceptionClass
) {
    public enum Category {
        TIMEOUT("timeout"),
        CONNECTION_RESET("connection_reset"),
        RATE_LIMIT("rate_limit"),
        SERVER_ERROR("server_error"),
        AUTHENTICATION("authentication"),
        QUOTA("quota"),
        MODEL("model"),
        NETWORK("network"),
        REQUEST("request"),
        UNKNOWN("unknown");

        private final String tag;

        Category(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }
}
