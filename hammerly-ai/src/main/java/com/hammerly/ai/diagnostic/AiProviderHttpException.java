package com.hammerly.ai.diagnostic;

import java.time.Duration;

/** Internal provider-shaped failure used by deterministic tests and simulators. */
public class AiProviderHttpException extends RuntimeException {
    private final int status;
    private final String code;
    private final Duration retryAfter;

    public AiProviderHttpException(int status, String code) {
        this(status, code, null);
    }

    public AiProviderHttpException(int status, String code, Duration retryAfter) {
        super("Provider HTTP " + status);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
