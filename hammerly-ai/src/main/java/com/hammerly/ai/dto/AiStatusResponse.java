package com.hammerly.ai.dto;

public record AiStatusResponse(String service, String status, boolean aiConfigured,
                               boolean redisEnabled, boolean kafkaEnabled) {
}
