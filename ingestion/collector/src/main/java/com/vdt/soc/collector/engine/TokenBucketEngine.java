package com.vdt.soc.collector.engine;

import com.vdt.soc.common.model.dto.PolicyDTO;
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
     * Attempt to consume a single token for the given tenant.
     *
     * @param tenantId tenant identifier
     * @param policy   the tenant's current policy (epsQuota, mode, burstMultiplier)
     * @return Mono.of(true) if token consumed (within quota),
     * Mono.of(false) if throttled (bucket empty)
     */
    Mono<Boolean> tryConsume(UUID tenantId, PolicyDTO policy);
}
