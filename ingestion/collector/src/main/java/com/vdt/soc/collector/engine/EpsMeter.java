package com.vdt.soc.collector.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Real-time EPS (Events Per Second) metering using Redis Sorted Sets.
 * <p>
 * Design:
 * - Two windows per tenant: 1-minute and 1-day
 * - Each event is recorded via ZADD with score = timestamp_ms
 * - Old entries are trimmed via ZREMRANGEBYSCORE
 * - Keys auto-expire to prevent memory leak
 * <p>
 * Key patterns:
 * eps:1m:{tenantId}  → ZSET, TTL 120s
 * eps:1d:{tenantId}  → ZSET, TTL 172800s (2 days)
 * <p>
 * Metering is fire-and-forget: event recording errors must not block
 * the response — they are logged and swallowed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EpsMeter {

    private static final String KEY_1M = "eps:1m:";
    private static final String KEY_1D = "eps:1d:";
    private static final long WINDOW_1M_MS = 60_000L;
    private static final long WINDOW_1D_MS = 86_400_000L;
    private static final Duration TTL_1M = Duration.ofSeconds(120);
    private static final Duration TTL_1D = Duration.ofSeconds(172800);

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * Record an event for the given tenant in both time windows.
     * <p>
     * Fire-and-forget: errors are logged but never propagated.
     *
     * @param tenantId tenant that produced the event
     * @return Mono that completes successfully regardless of Redis errors
     */
    public Mono<Void> record(UUID tenantId) {
        long nowMs = System.currentTimeMillis();
        String eventId = UUID.randomUUID().toString();
        String score = String.valueOf(nowMs);

        String key1m = KEY_1M + tenantId;
        String key1d = KEY_1D + tenantId;

        return Mono.when(
                        // Add to 1-minute window → trim → set TTL
                        redisTemplate.opsForZSet().add(key1m, eventId, nowMs)
                                .then(redisTemplate.opsForZSet()
                                        .removeRangeByScore(key1m,
                                                org.springframework.data.domain.Range.closed(0.0, (double) (nowMs - WINDOW_1M_MS))))
                                .then(redisTemplate.expire(key1m, TTL_1M)),

                        // Add to 1-day window → trim → set TTL
                        redisTemplate.opsForZSet().add(key1d, eventId, nowMs)
                                .then(redisTemplate.opsForZSet()
                                        .removeRangeByScore(key1d,
                                                org.springframework.data.domain.Range.closed(0.0, (double) (nowMs - WINDOW_1D_MS))))
                                .then(redisTemplate.expire(key1d, TTL_1D))
                )
                .then()
                .onErrorResume(e -> {
                    log.warn("EPS metering error for tenant={}: {}", tenantId, e.getMessage());
                    return Mono.empty();
                });
    }
}
