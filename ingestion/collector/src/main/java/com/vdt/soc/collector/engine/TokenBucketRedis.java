package com.vdt.soc.collector.engine;

import com.vdt.soc.common.core.dto.PolicyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Token Bucket implementation using Redis Lua script for atomicity.
 * <p>
 * Bucket key:  "bucket:{tenantId}"
 * Capacity:    epsQuota * burstMultiplier
 * Refill rate: epsQuota tokens/sec
 * <p>
 * The Lua script reads current tokens, refills based on elapsed time,
 * attempts to consume, and persists the updated state — all in one
 * atomic Redis operation. No race conditions across collector instances.
 * <p>
 * Supports partial consumption: if the bucket has fewer tokens than
 * requested, all available tokens are consumed and returned.
 */
@Slf4j
@RequiredArgsConstructor
public class TokenBucketRedis implements TokenBucketEngine {

    private static final String BUCKET_KEY_PREFIX = "bucket:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> script;

    @Override
    public Mono<Long> tryConsume(UUID tenantId, PolicyDTO policy, long requested) {
        // Default policy → throttle everything
        if (policy.isDefault()) {
            return Mono.just(0L);
        }

        String bucketKey = BUCKET_KEY_PREFIX + tenantId.toString();
        long capacity = (long) (policy.getEpsQuota() * policy.getBurstMultiplier());
        long refillRate = policy.getEpsQuota();
        long nowMs = System.currentTimeMillis();

        if (capacity <= 0 || requested <= 0) {
            return Mono.just(0L);
        }

        return redisTemplate.execute(script,
                        List.of(bucketKey),
                        List.of(
                                String.valueOf(capacity),
                                String.valueOf(refillRate),
                                String.valueOf(nowMs),
                                String.valueOf(requested)
                        ))
                .singleOrEmpty()
                .map(result -> {
                    if (result == null || result.isEmpty()) {
                        return 0L;
                    }
                    return toLong(result.getFirst());
                })
                .onErrorResume(e -> {
                    log.error("Token bucket Redis error for tenant={}: {}", tenantId, e.getMessage());
                    // Fail open: allow all requested events on Redis error
                    return Mono.just(requested);
                });
    }

    /**
     * Safely convert a Lua script result element to Long.
     */
    private static Long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
