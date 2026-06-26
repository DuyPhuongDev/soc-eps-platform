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
 * - Two windows per category: 1-minute and 1-day
 * - Per-second buckets: each hash field = epochSecond, value = event count
 * - HINCRBY is O(1) atomic — multiple events in the same second are merged
 * - Old fields are cleaned up by Redis key expiry (no manual trim needed)
 * <p>
 * Key patterns:
 * eps:1m:ok:{tenantId}    → Hash, field=epochSecond, TTL 120s
 * eps:1m:drop:{tenantId}  → Hash, field=epochSecond, TTL 120s
 * eps:1d:ok:{tenantId}    → Hash, field=epochSecond, TTL 172800s
 * eps:1d:drop:{tenantId}  → Hash, field=epochSecond, TTL 172800s
 * <p>
 * Query pattern (Lua script, not yet implemented):
 * SUM of all fields in the hash = EPS for that window.
 * Incoming = SUM(ok) + SUM(drop).
 * <p>
 * Metering is fire-and-forget: event recording errors must not block
 * the response — they are logged and swallowed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EpsMeter {

    private static final String KEY_1M_OK = "eps:1m:ok:";
    private static final String KEY_1M_DROP = "eps:1m:drop:";
    private static final String KEY_1D_OK = "eps:1d:ok:";
    private static final String KEY_1D_DROP = "eps:1d:drop:";

    private static final Duration TTL_1M = Duration.ofSeconds(120);
    private static final Duration TTL_1D = Duration.ofSeconds(172800);

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
        return incrementAndExpire(KEY_1M_OK + tenantId, field, count, TTL_1M)
                .then(incrementAndExpire(KEY_1D_OK + tenantId, field, count, TTL_1D));
    }

    /**
     * Record {@code count} dropped events for the given tenant.
     */
    public Mono<Void> recordDropped(UUID tenantId, int count) {
        if (count <= 0) {
            return Mono.empty();
        }
        String field = epochSecond();
        return incrementAndExpire(KEY_1M_DROP + tenantId, field, count, TTL_1M)
                .then(incrementAndExpire(KEY_1D_DROP + tenantId, field, count, TTL_1D));
    }

    /**
     * HINCRBY + refresh TTL on a hash key.
     * <p>
     * TTL is refreshed on every write so the hash only expires after
     * a full TTL window of inactivity — matching the old ZSET behavior.
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
