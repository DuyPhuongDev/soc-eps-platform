package com.vdt.soc.collector.engine;

import com.vdt.soc.collector.cache.ApiKeyCache;
import com.vdt.soc.collector.cache.PolicyCache;
import com.vdt.soc.collector.dto.EventRequest;
import com.vdt.soc.collector.dto.EventResponse;
import com.vdt.soc.common.core.util.HashUtil;
import com.vdt.soc.common.model.dto.PolicyDTO;
import com.vdt.soc.collector.exception.ThrottledException;
import com.vdt.soc.collector.exception.UnauthorizedException;
import com.vdt.soc.collector.forward.KafkaEventForwarder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Core enforcement engine — orchestrates the event processing pipeline:
 *
 * 1. Auth — resolve API key hash → tenantId (via ApiKeyCache)
 * 2. Policy — look up tenant's license policy (via PolicyCache)
 * 3. Rate Limit — try to consume a token (via TokenBucketEngine)
 * 4. Meter — record event for EPS tracking (via EpsMeter)
 * 5. Forward — publish to Kafka (via KafkaEventForwarder)
 * 6. Response — return 202 or error
 *
 * Every step returns Mono (non-blocking). The entire chain is composed
 * reactively — no thread is ever blocked.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnforcementEngine {

    private final ApiKeyCache apiKeyCache;
    private final PolicyCache policyCache;
    private final TokenBucketEngine tokenBucket;
    private final EpsMeter epsMeter;
    private final KafkaEventForwarder forwarder;

    /**
     * Process an event through the enforcement pipeline.
     *
     * @param event      the inbound event payload
     * @param rawApiKey  the plaintext API key from X-API-Key header
     * @return Mono<EventResponse> on success, Mono.error on failure
     */
    public Mono<EventResponse> process(EventRequest event, String rawApiKey) {
        String hash = HashUtil.sha256Hex(rawApiKey);

        return resolveTenant(hash)
                .flatMap(tenantId -> checkQuotaAndForward(event, tenantId))
                .map(tenantId -> EventResponse.builder()
                        .tenantId(tenantId)
                        .status("accepted")
                        .traceId(UUID.randomUUID().toString())
                        .build())
                .doOnSuccess(resp -> log.debug("Event accepted: tenant={}, traceId={}",
                        resp.getTenantId(), resp.getTraceId()))
                .doOnError(e -> {
                    if (e instanceof UnauthorizedException || e instanceof ThrottledException) {
                        log.debug("Event rejected: {}", e.getMessage());
                    } else {
                        log.error("Event processing failed: {}", e.getMessage());
                    }
                });
    }

    /**
     * Resolve API key hash to tenantId.
     * Cache miss → immediate 401.
     */
    private Mono<UUID> resolveTenant(String apiKeyHash) {
        UUID tenantId = apiKeyCache.resolve(apiKeyHash);
        if (tenantId == null) {
            return Mono.error(new UnauthorizedException("Invalid API key"));
        }
        return Mono.just(tenantId);
    }

    /**
     * Token bucket check → metering → Kafka forward.
     * Throttle → immediate 429.
     * Kafka failure → 503.
     */
    private Mono<UUID> checkQuotaAndForward(EventRequest event, UUID tenantId) {
        PolicyDTO policy = policyCache.get(tenantId);

        return tokenBucket.tryConsume(tenantId, policy)
                .flatMap(allowed -> {
                    if (!Boolean.TRUE.equals(allowed)) {
                        return Mono.error(new ThrottledException(
                                "EPS limit exceeded for tenant " + tenantId));
                    }
                    // Allowed → record meter + forward to Kafka
                    return epsMeter.record(tenantId)
                            .then(forwarder.send(event, tenantId))
                            .thenReturn(tenantId);
                });
    }
}
