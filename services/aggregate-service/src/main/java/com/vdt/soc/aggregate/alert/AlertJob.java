package com.vdt.soc.aggregate.alert;

import com.vdt.soc.aggregate.cache.PolicyCache;
import com.vdt.soc.common.core.dto.PolicyDTO;
import com.vdt.soc.common.core.enumeration.AlertSeverity;
import com.vdt.soc.common.core.enumeration.AlertType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertJob {

    private final PolicyCache policyCache;
    private final AlertEventPublisher alertEventPublisher;
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_OK = "eps:ok:";
    private static final String KEY_DROP = "eps:drop:";


    private final Map<String, Boolean> activeAlerts = new ConcurrentHashMap<>();

    private static final int WINDOW_60S = 60;

    @Scheduled(fixedDelay = 30_000)
    public void checkAlerts() {
        log.debug("AlertJob started");
        long nowSec = System.currentTimeMillis() / 1000;
        for (PolicyDTO policy : policyCache.snapshot()) {
            try {
                WindowResult accepted = sumHashFieldsWindow(KEY_OK + policy.getTenantId(), nowSec - WINDOW_60S);
                WindowResult dropped  = sumHashFieldsWindow(KEY_DROP + policy.getTenantId(), nowSec - WINDOW_60S);
                evaluate(policy, accepted, dropped);
            } catch (Exception e) {
                log.warn("Alert check failed for tenant={}: {}", policy.getTenantId(), e.getMessage());
            }
        }
        log.debug("AlertJob completed");
    }


    private void evaluate(PolicyDTO policy, WindowResult accepted, WindowResult dropped) {
        if (policy.isDefault()) {
            return;
        }

        UUID tenantId = policy.getTenantId();
        int epsQuota = policy.getEpsQuota();

        int actualSec = accepted.fields > 0 ? accepted.fields : 1;
        double acceptedEps = accepted.sum / (double) actualSec;

        String key100 = tenantId + ":EPS_100_PCT";
        String key70  = tenantId + ":EPS_70_PCT";

        if (dropped.sum > 0 || epsQuota > 0 && acceptedEps >= epsQuota) {
            // CRITICAL: throttling — events are being dropped
            if (!isActive(key100)) {
                alertEventPublisher.fire(tenantId, AlertType.EPS_100_PCT,
                        AlertSeverity.CRITICAL,
                        String.format("Tenant was throttled: %.1f accepted-eps over %ds (quota %d) — dropped %d events",
                                acceptedEps, actualSec, epsQuota, dropped.sum),
                        acceptedEps, (double) epsQuota);
                setActive(key100);
            }
        } else if (epsQuota > 0 && acceptedEps >= 0.7 * epsQuota) {
            // 70–99% — WARNING
            if (!isActive(key70)) {
                double usagePct = Math.round(acceptedEps / epsQuota * 1000.0) / 10.0;
                alertEventPublisher.fire(tenantId, AlertType.EPS_70_PCT,
                        AlertSeverity.WARNING,
                        String.format("EPS (%.1f) over %ds reached %.1f%% quota (%d eps) — early warning",
                                acceptedEps, actualSec, usagePct, epsQuota),
                        acceptedEps, (double) epsQuota);
                setActive(key70);
            }
            // EPS dropped from ≥100% to 70-99% — clear key100
            setInactive(key100);

        } else {
            // Below 70% — clear both
            setInactive(key70);
            setInactive(key100);
        }
    }

    private boolean isActive(String key) {
        return Boolean.TRUE.equals(activeAlerts.get(key));
    }

    private void setActive(String key) {
        activeAlerts.put(key, true);
    }

    private void setInactive(String key) {
        activeAlerts.remove(key);
    }

    private record WindowResult(long sum, int fields) {
        static final WindowResult EMPTY = new WindowResult(0, 0);
    }

    private WindowResult sumHashFieldsWindow(String key, long fromSec) {
        try {
            long[] sum = {0};
            int[] count = {0};
            redisTemplate.opsForHash().entries(key).forEach((k, v) -> {
                try {
                    if (Long.parseLong(k.toString()) >= fromSec) {
                        sum[0] += Long.parseLong(v.toString());
                        count[0]++;
                    }
                } catch (NumberFormatException ignored) {
                    // skip malformed fields
                }
            });
            return count[0] > 0 ? new WindowResult(sum[0], count[0]) : WindowResult.EMPTY;
        } catch (Exception e) {
            log.debug("Failed to read Redis hash {}: {}", key, e.getMessage());
            return WindowResult.EMPTY;
        }
    }
}
