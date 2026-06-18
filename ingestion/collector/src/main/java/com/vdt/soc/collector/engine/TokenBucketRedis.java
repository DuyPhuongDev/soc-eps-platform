package com.vdt.soc.collector.engine;

import com.vdt.soc.common.model.dto.PolicyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Token Bucket implementation using Redis Lua script for atomicity.
 *
 * Bucket key:  "bucket:{tenantId}"
 * Capacity:    epsQuota * burstMultiplier
 * Refill rate: epsQuota tokens/sec
 *
 * The Lua script reads current tokens, refills based on elapsed time,
 * attempts to consume, and persists the updated state — all in one
 * atomic Redis operation. No race conditions across collector instances.
 */
@Slf4j
@RequiredArgsConstructor
public class TokenBucketRedis implements TokenBucketEngine {

    private static final String BUCKET_KEY_PREFIX = "bucket:";
    private static final long REQUESTED_TOKENS = 1L;

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> script;

    @Override
    public Mono<Boolean> tryConsume(UUID tenantId, PolicyDTO policy) {
        // Default policy → throttle everything
        if (policy.isDefault()) {
            return Mono.just(false);
        }

        String bucketKey = BUCKET_KEY_PREFIX + tenantId.toString();
        long capacity = (long) (policy.getEpsQuota() * policy.getBurstMultiplier());
        long refillRate = policy.getEpsQuota();
        long nowMs = System.currentTimeMillis();

        if (capacity <= 0) {
            return Mono.just(false);
        }

        return redisTemplate.execute(script,
                        List.of(bucketKey),
                        List.of(
                                String.valueOf(capacity),
                                String.valueOf(refillRate),
                                String.valueOf(nowMs),
                                String.valueOf(REQUESTED_TOKENS)
                        ))
                .singleOrEmpty()
                .map(result -> {
                    if (result == null || result.isEmpty()) {
                        return false;
                    }
                    return Long.valueOf(1).equals(result.getFirst());
                })
                .onErrorResume(e -> {
                    log.error("Token bucket Redis error for tenant={}: {}", tenantId, e.getMessage());
                    // Fail open: allow event on Redis error (prefer availability over enforcement)
                    return Mono.just(true);
                });
    }
}
