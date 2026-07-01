package com.vdt.soc.collector.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Real-time EPS (Events Per Second) metering using Redis Hashes.
 * <p>
 * Design:
 * - Two event categories per tenant: accepted and dropped
 * - Incoming rate is derived: incoming = accepted + dropped
 * - Single per-second bucket per category (aggregate service handles
 *   all time-window aggregation: minute → timeseries_data, day → stats:drop:daily:*)
 * - Per-second buckets: each hash field = epochSecond, value = event count
 * - HINCRBY is O(1) atomic — multiple events in the same second are merged
 * - Old fields are cleaned up by Redis key expiry (no manual trim needed)
 * <p>
 * Key patterns:
 * eps:ok:{tenantId}      → Hash, field=epochSecond, TTL 120s
 * eps:drop:{tenantId}    → Hash, field=epochSecond, TTL 172800s
 * <p>
 * Metering is fire-and-forget: event recording errors must not block
 * the response — they are logged and swallowed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EpsMeter {

    private static final String KEY_OK = "eps:ok:";
    private static final String KEY_DROP = "eps:drop:";

    /** 120s — aggregate service snapshots every 60s, so this gives one full retry window. */
    private static final Duration TTL_OK = Duration.ofSeconds(120);
    /** 48h — DropStatsService needs up to 24h of per-second history for hourly "day" aggregation. */
    private static final Duration TTL_DROP = Duration.ofSeconds(172800);

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * Record a single accepted event for the given tenant.
     */
    public Mono<Void> recordAccepted(UUID tenantId) {
        return recordAccepted(tenantId, 1);
    }

    /**
     * Record a single dropped event for the given tenant.
     */
    public Mono<Void> recordDropped(UUID tenantId) {
        return recordDropped(tenantId, 1);
    }

    /**
     * Record {@code count} accepted events for the given tenant.
     */
    public Mono<Void> recordAccepted(UUID tenantId, int count) {
        if (count <= 0) {
            return Mono.empty();
        }
        String field = epochSecond();
        return incrementAndExpire(KEY_OK + tenantId, field, count, TTL_OK);
    }

    /**
     * Record {@code count} dropped events for the given tenant.
     */
    public Mono<Void> recordDropped(UUID tenantId, int count) {
        if (count <= 0) {
            return Mono.empty();
        }
        String field = epochSecond();
        return incrementAndExpire(KEY_DROP + tenantId, field, count, TTL_DROP);
    }

    /**
     * HINCRBY + refresh TTL on a hash key.
     * <p>
     * TTL is refreshed on every write so the hash only expires after
     * a full TTL window of inactivity.
     */
    private Mono<Void> incrementAndExpire(String key, String field, int count, Duration ttl) {
        return redisTemplate.<String, String>opsForHash()
                .increment(key, field, count)
                .then(redisTemplate.expire(key, ttl))
                .then()
                .onErrorResume(e -> {
                    log.warn("EPS metering error for key={}: {}", key, e.getMessage());
                    return Mono.empty();
                });
    }

    private static String epochSecond() {
        return String.valueOf(System.currentTimeMillis() / 1000);
    }
}
