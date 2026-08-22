package com.hammerly.worker.consumer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EventTypeExtractor {
    private static final Pattern EVENT_TYPE = Pattern.compile(
        "\\\"eventType\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private EventTypeExtractor() {
    }

    public static String fromJson(String json) {
        if (json == null) {
            return "unknown";
        }
        Matcher matcher = EVENT_TYPE.matcher(json);
        return matcher.find() ? matcher.group(1) : "unknown";
    }
}
