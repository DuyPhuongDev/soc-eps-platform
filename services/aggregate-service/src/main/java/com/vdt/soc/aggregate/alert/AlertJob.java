package com.vdt.soc.aggregate.alert;

import com.vdt.soc.aggregate.cache.PolicyCache;
import com.vdt.soc.common.core.dto.PolicyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that evaluates EPS threshold conditions every 30 seconds.
 * Iterates over all active tenants from PolicyCache, reads current
 * EPS data from Redis, and publishes alert events to Kafka via
 * {@link AlertEventPublisher} (consumed by notification-service).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertJob {

    private final PolicyCache policyCache;
    private final AlertEventPublisher alertEventPublisher;
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_OK = "eps:ok:";
    private static final String KEY_DROP = "eps:drop:";

    @Scheduled(fixedDelay = 30_000)
    public void checkAlerts() {
        log.debug("AlertJob started");
        for (PolicyDTO policy : policyCache.snapshot()) {
            try {
                long accepted1m = sumHashValues(KEY_OK + policy.getTenantId());
                long dropped1m = sumHashValues(KEY_DROP + policy.getTenantId());
                evaluate(policy, accepted1m, dropped1m);
            } catch (Exception e) {
                log.warn("Alert check failed for tenant={}: {}", policy.getTenantId(), e.getMessage());
            }
        }
        log.debug("AlertJob completed");
    }

    /**
     * Evaluate alert conditions and publish to Kafka.
     * Debounce will be handled by notification-service.
     */
    private void evaluate(PolicyDTO policy, long accepted1m, long dropped1m) {
        if (policy.isDefault()) {
            return;
        }

        double eps = (accepted1m + dropped1m) / 60.0;
        int epsQuota = policy.getEpsQuota();

        if (dropped1m > 0) {
            // CRITICAL: throttling — events are being dropped
            alertEventPublisher.fire(
                    policy.getTenantId(), null, "EPS_100_PCT", "CRITICAL",
                    String.format("EPS (%.0f) reached %d quota, tenant was throttling — dropped %d events",
                            eps, epsQuota, dropped1m),
                    null);
            // Also resolve EPS_70_PCT since we're past that threshold
            alertEventPublisher.resolve(policy.getTenantId(), "EPS_70_PCT");

        } else if (eps >= 0.7 * epsQuota) {
            double usagePct = Math.round(eps / epsQuota * 100.0 * 10.0) / 10.0;
            alertEventPublisher.fire(
                    policy.getTenantId(), null, "EPS_70_PCT", "WARNING",
                    String.format("EPS (%.1f) đạt %.1f%% quota (%d eps) — cảnh báo sớm",
                            eps, usagePct, epsQuota),
                    null);

        } else {
            // Below 70% — resolve active EPS alerts
            alertEventPublisher.resolve(policy.getTenantId(), "EPS_70_PCT");
            alertEventPublisher.resolve(policy.getTenantId(), "EPS_100_PCT");
        }
    }

    /**
     * SUM all values in a Redis hash. Returns 0 if key doesn't exist.
     */
    private long sumHashValues(String key) {
        try {
            return redisTemplate.opsForHash().values(key).stream()
                    .mapToLong(v -> Long.parseLong(v.toString()))
                    .sum();
        } catch (Exception e) {
            log.debug("Failed to read Redis hash {}: {}", key, e.getMessage());
            return 0;
        }
    }
}
