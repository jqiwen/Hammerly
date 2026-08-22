package com.hammerly.ai.redis;

public record RedisCounter(long count, long ttlSeconds) {
}
