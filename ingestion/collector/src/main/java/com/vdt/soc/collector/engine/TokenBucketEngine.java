package com.vdt.soc.collector.engine;

import com.vdt.soc.common.core.dto.PolicyDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Interface for the token bucket rate limiter.
 * <p>
 * Implementations must be thread-safe and atomic
 * (Redis Lua script is the production implementation).
 */
public interface TokenBucketEngine {

    /**
     * Attempt to consume many tokens for the given tenant.
     * <p>
     * Supports partial consumption: if the bucket has fewer tokens than
     * requested, all available tokens are consumed and the count is returned.
     * This enables batch processing with partial accept.
     *
     * @param tenantId  tenant identifier
     * @param policy    the tenant's current policy (epsQuota, mode, burstMultiplier)
     * @param requested number of tokens to attempt to consume
     * @return Mono of tokens actually consumed (0 if bucket is empty)
     */
    Mono<Long> tryConsume(UUID tenantId, PolicyDTO policy, long requested);

    /**
     * Attempt to consume a single token for the given tenant.
     * <p>
     * Convenience overload for single-event ingestion.
     *
     * @param tenantId tenant identifier
     * @param policy   the tenant's current policy (epsQuota, mode, burstMultiplier)
     * @return Mono.of(true) if token consumed (within quota),
     * Mono.of(false) if throttled (bucket empty)
     */
    default Mono<Boolean> tryConsume(UUID tenantId, PolicyDTO policy) {
        return tryConsume(tenantId, policy, 1L)
                .map(consumed -> consumed > 0);
    }
}
