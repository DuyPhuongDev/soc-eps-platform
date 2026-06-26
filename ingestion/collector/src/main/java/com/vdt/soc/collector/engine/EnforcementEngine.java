package com.vdt.soc.collector.engine;

import com.vdt.soc.collector.cache.ApiKeyCache;
import com.vdt.soc.collector.cache.PolicyCache;
import com.vdt.soc.collector.dto.EventBatchRequest;
import com.vdt.soc.collector.dto.EventBatchResponse;
import com.vdt.soc.collector.dto.EventRequest;
import com.vdt.soc.collector.dto.EventResponse;
import com.vdt.soc.collector.exception.ThrottledException;
import com.vdt.soc.collector.forward.KafkaEventForwarder;
import com.vdt.soc.common.core.dto.PolicyDTO;
import com.vdt.soc.common.core.exception.UnauthorizedException;
import com.vdt.soc.common.core.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Core enforcement engine — orchestrates the event processing pipeline:
 * <p>
 * 1. Auth — resolve API key hash → tenantId (via ApiKeyCache)
 * 2. Policy — look up tenant's license policy (via PolicyCache)
 * 3. Rate Limit — try to consume a token (via TokenBucketEngine)
 * 4. Meter — record accepted / dropped events (via EpsMeter)
 * 5. Forward — publish to Kafka (via KafkaEventForwarder)
 * 6. Response — return 202 or error
 * <p>
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
     * @param event     the inbound event payload
     * @param rawApiKey the plaintext API key from X-API-Key header
     * @return Mono&lt;EventResponse&gt; on success, Mono.error on failure
     */
    public Mono<EventResponse> process(EventRequest event, String rawApiKey) {
        String hash = HashUtil.sha256Hex(rawApiKey);
        log.debug("Api key after hash {}", hash);

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
     * Resolve API key hash to tenantId via in-memory cache.
     * Populated by etcd watch (real-time) with poll fallback.
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
     * Process a batch of events through the enforcement pipeline.
     * <p>
     * Multi-token check → meter accepted/dropped → Kafka forward.
     * Full throttle → 429. Partial accept → 202 with rejected count.
     * Incoming rate is derived: incoming = accepted + dropped.
     *
     * @param batch     the inbound batch payload
     * @param rawApiKey the plaintext API key from X-API-Key header
     * @return Mono&lt;EventBatchResponse&gt; on success, Mono.error on failure
     */
    public Mono<EventBatchResponse> processBatch(EventBatchRequest batch, String rawApiKey) {
        String hash = HashUtil.sha256Hex(rawApiKey);
        log.debug("Api key after hash {}", hash);
        int batchSize = batch.getEvents().size();

        return resolveTenant(hash)
                .flatMap(tenantId -> {
                    PolicyDTO policy = policyCache.get(tenantId);

                    return tokenBucket.tryConsume(tenantId, policy, batchSize)
                            .flatMap(allowedCount -> {
                                int allowed = allowedCount.intValue();
                                int rejected = batchSize - allowed;

                                if (allowed == 0) {
                                    // All throttled → meter batch as dropped → error
                                    return epsMeter.recordDropped(tenantId, batchSize)
                                            .then(Mono.error(new ThrottledException(
                                                    "EPS limit exceeded for tenant " + tenantId)));
                                }

                                List<EventRequest> accepted = batch.getEvents();
                                List<EventRequest> allowedEvents = allowed >= batchSize
                                        ? accepted
                                        : accepted.subList(0, allowed);

                                String traceId = UUID.randomUUID().toString();

                                // Meter accepted + dropped by count (single Redis op per category)
                                Mono<Void> meterAccepted = epsMeter.recordAccepted(tenantId, allowed);
                                Mono<Void> meterDropped = epsMeter.recordDropped(tenantId, rejected);

                                return Mono.when(meterAccepted, meterDropped)
                                        .then(forwarder.sendBatch(allowedEvents, tenantId))
                                        .then(Mono.just(EventBatchResponse.builder()
                                                .tenantId(tenantId)
                                                .traceId(traceId)
                                                .accepted(allowed)
                                                .rejected(rejected)
                                                .status(rejected == 0 ? "accepted" : "partial")
                                                .build()));
                            });
                })
                .doOnSuccess(resp -> log.debug("Batch processed: tenant={}, accepted={}, rejected={}, traceId={}",
                        resp.getTenantId(), resp.getAccepted(), resp.getRejected(), resp.getTraceId()))
                .doOnError(e -> {
                    if (e instanceof UnauthorizedException || e instanceof ThrottledException) {
                        log.debug("Batch rejected: {}", e.getMessage());
                    } else {
                        log.error("Batch processing failed: {}", e.getMessage());
                    }
                });
    }

    /**
     * Token bucket check → meter accepted/dropped → Kafka forward.
     * Throttle → immediate 429. Kafka failure → 503.
     * Incoming rate is derived: incoming = accepted + dropped.
     */
    private Mono<UUID> checkQuotaAndForward(EventRequest event, UUID tenantId) {
        PolicyDTO policy = policyCache.get(tenantId);

        return tokenBucket.tryConsume(tenantId, policy)
                .flatMap(allowed -> {
                    if (!Boolean.TRUE.equals(allowed)) {
                        // Meter dropped before throwing error
                        return epsMeter.recordDropped(tenantId)
                                .then(Mono.error(new ThrottledException(
                                        "EPS limit exceeded for tenant " + tenantId)));
                    }
                    // Meter accepted + forward to Kafka
                    return epsMeter.recordAccepted(tenantId)
                            .then(forwarder.send(event, tenantId))
                            .thenReturn(tenantId);
                });
    }
}
