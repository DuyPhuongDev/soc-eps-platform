package com.vdt.soc.collector.engine;

import com.vdt.soc.common.core.dto.PolicyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Enforces monthly event-volume quota using Redis atomic counters.
 * <p>
 * Window keys are derived from the license start date, not wall-clock time:
 * <ul>
 *   <li>Monthly window: {@code floor((now - startDate) / 30d)} — resets each 30d</li>
 * </ul>
 * <p>
 * When a license is renewed (startDate changes), the window key naturally
 * shifts forward, effectively resetting the counters.
 * <p>
 * Lua script atomically checks + increments the monthly counter.
 * If the quota would be exceeded, no increment occurs and the call returns
 * {@code 0}. Otherwise the counter is incremented and the call returns
 * {@code requested}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaEnforcer {

    private static final String MONTHLY_KEY_PREFIX = "quota:monthly:";
    private static final long MONTHLY_WINDOW_SEC = Duration.ofDays(30).toSeconds();

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List<Object>> quotaScript;

    /**
     * Check and increment monthly quota counter for the given tenant.
     *
     * @param tenantId   the tenant to check
     * @param policy     the active policy (carries monthlyQuota, and startDate-derived window anchor)
     * @param startDate  the license start date (window anchor)
     * @param requested  number of tokens requested
     * @return Mono of the number of tokens allowed (requested if under quota, or 0 if exceeded)
     */
    public Mono<Long> tryConsume(UUID tenantId, PolicyDTO policy, Instant startDate, long requested) {
        // Default policy → block everything
        if (policy.isDefault()) {
            return Mono.just(0L);
        }

        long monthlyQuota = policy.getMonthlyQuota() != null ? policy.getMonthlyQuota() : 0L;

        if (monthlyQuota <= 0 || requested <= 0) {
            return Mono.just(0L);
        }

        long nowSec = System.currentTimeMillis() / 1000;
        long startSec = startDate.getEpochSecond();

        long monthlyWindow = Math.max(0, (nowSec - startSec) / MONTHLY_WINDOW_SEC);

        String monthlyKey = MONTHLY_KEY_PREFIX + tenantId + ":" + monthlyWindow;

        return redisTemplate.execute(quotaScript,
                        List.of(monthlyKey),
                        List.of(
                                String.valueOf(monthlyQuota),
                                String.valueOf(requested),
                                String.valueOf(MONTHLY_WINDOW_SEC + 86400) // TTL: window + 1d buffer
                        ))
                .singleOrEmpty()
                .map(result -> {
                    if (result == null || result.isEmpty()) {
                        return 0L;
                    }
                    return toLong(result.getFirst());
                })
                .onErrorResume(e -> {
                    log.error("Quota enforcement Redis error for tenant={}: {}", tenantId, e.getMessage());
                    // Fail open: allow events on Redis error
                    return Mono.just(requested);
                });
    }

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
