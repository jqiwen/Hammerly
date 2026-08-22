package com.hammerly.worker.idempotency;

import com.hammerly.worker.config.WorkerProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisIdempotencyStore implements IdempotencyStore {
    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[1]) == 1 then
          return 0
        end
        if redis.call('SET', KEYS[2], ARGV[1], 'NX', 'PX', ARGV[2]) then
          return 1
        end
        return 2
        """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then
          return redis.call('DEL', KEYS[1])
        end
        return 0
        """, Long.class);

    private final StringRedisTemplate redis;
    private final WorkerProperties properties;

    public RedisIdempotencyStore(StringRedisTemplate redis, WorkerProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public ProcessingClaim claim(UUID eventId) {
        String lockToken = UUID.randomUUID().toString();
        Long result = redis.execute(CLAIM_SCRIPT,
            List.of(processedKey(eventId), lockKey(eventId)), lockToken,
            Long.toString(properties.processingLockTtl().toMillis()));
        if (result == null) {
            throw new IllegalStateException("Redis returned no idempotency claim result");
        }
        return switch (result.intValue()) {
            case 0 -> new ProcessingClaim(eventId,
                ProcessingClaim.Status.ALREADY_PROCESSED, null);
            case 1 -> new ProcessingClaim(eventId, ProcessingClaim.Status.ACQUIRED, lockToken);
            case 2 -> new ProcessingClaim(eventId, ProcessingClaim.Status.IN_PROGRESS, null);
            default -> throw new IllegalStateException("Unexpected idempotency claim result");
        };
    }

    @Override
    public void complete(ProcessingClaim claim) {
        redis.opsForValue().set(processedKey(claim.eventId()), "1",
            properties.processedEventTtl());
        deleteOwnedLock(claim);
    }

    @Override
    public void release(ProcessingClaim claim) {
        deleteOwnedLock(claim);
    }

    private void deleteOwnedLock(ProcessingClaim claim) {
        if (claim.lockToken() != null) {
            redis.execute(RELEASE_SCRIPT, List.of(lockKey(claim.eventId())), claim.lockToken());
        }
    }

    private String processedKey(UUID eventId) {
        return "hammerly:worker:processed:" + eventId;
    }

    private String lockKey(UUID eventId) {
        return "hammerly:worker:processing:" + eventId;
    }
}
