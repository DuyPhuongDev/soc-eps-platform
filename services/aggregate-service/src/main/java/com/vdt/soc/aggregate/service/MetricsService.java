package com.vdt.soc.aggregate.service;

import com.vdt.soc.aggregate.cache.PolicyCache;
import com.vdt.soc.aggregate.dto.MetricsResponse;
import com.vdt.soc.aggregate.dto.TimeseriesResponse;
import com.vdt.soc.aggregate.entity.TimeSeriesData;
import com.vdt.soc.aggregate.repository.TimeSeriesRepository;
import com.vdt.soc.common.core.dto.PolicyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final StringRedisTemplate redisTemplate;
    private final PolicyCache policyCache;
    private final TimeSeriesRepository timeSeriesRepo;

    private static final String KEY_OK = "eps:ok:";
    private static final String KEY_DROP = "eps:drop:";
    private static final String KEY_MONTHLY = "quota:monthly:";
    private static final Duration MONTHLY_WINDOW = Duration.ofDays(30);

    /**
     * Get current EPS and monthly usage snapshot for a tenant.
     *
     * @return MetricsResponse, or null if tenant has no active policy
     */
    public MetricsResponse getCurrentMetrics(UUID tenantId) {
        PolicyDTO policy = policyCache.get(tenantId);
        if (policy.isDefault()) {
            log.debug("No active policy for tenant {}", tenantId);
            return null;
        }

        double acceptedEps = sumHashToEps(KEY_OK + tenantId);
        double droppedEps = sumHashToEps(KEY_DROP + tenantId);
        double totalEps = acceptedEps + droppedEps;
        double epsUsagePct = policy.getEpsQuota() > 0
                ? Math.round(totalEps / policy.getEpsQuota() * 1000.0) / 10.0
                : 0.0;

        long monthlyUsed = getMonthlyUsed(policy);
        long monthlyQuota = policy.getMonthlyQuota() != null ? policy.getMonthlyQuota() : 0;
        double monthlyUsagePct = monthlyQuota > 0
                ? Math.round((double) monthlyUsed / monthlyQuota * 1000.0) / 10.0
                : 0.0;

        return MetricsResponse.builder()
                .tenantId(tenantId)
                .epsQuota(policy.getEpsQuota())
                .plan(policy.getPlan())
                .eps(MetricsResponse.EpsMetrics.builder()
                        .accepted(acceptedEps)
                        .dropped(droppedEps)
                        .total(totalEps)
                        .usagePct(epsUsagePct)
                        .build())
                .monthly(MetricsResponse.MonthlyMetrics.builder()
                        .used(monthlyUsed)
                        .quota(monthlyQuota)
                        .usagePct(monthlyUsagePct)
                        .build())
                .build();
    }

    /**
     * Build Grafana-compatible time-series response from timeseries_data table.
     *
     * @param tenantId  tenant UUID
     * @param from      start of time range
     * @param to        end of time range
     * @param interval  bucket size: "1m", "5m", "15m", "1h"
     * @param targets   list of metric names to include
     * @return list of TimeseriesResponse (one per target)
     */
    public List<TimeseriesResponse> buildTimeseries(UUID tenantId, Instant from, Instant to,
                                                     String interval, List<String> targets) {
        PolicyDTO policy = policyCache.get(tenantId);
        long intervalSeconds = parseIntervalSeconds(interval);
        int epsQuota = policy.getEpsQuota();
        long monthlyQuota = policy.getMonthlyQuota() != null ? policy.getMonthlyQuota() : 0;

        // Query raw data from DB
        List<TimeSeriesData> rows = timeSeriesRepo
                .findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(tenantId, from, to);

        // Build a map of timestamp → row for efficient lookup
        Map<Instant, TimeSeriesData> rowMap = new LinkedHashMap<>();
        for (TimeSeriesData row : rows) {
            rowMap.put(row.getBucketTime(), row);
        }

        // Generate expected bucket timestamps
        List<Instant> expectedBuckets = generateBuckets(from, to, intervalSeconds);

        // Build datapoints per target
        Map<String, List<List<Number>>> targetDatapoints = new LinkedHashMap<>();
        for (String target : targets) {
            targetDatapoints.put(target, new ArrayList<>());
        }

        long monthlyWindow = computeMonthlyWindow(policy);
        for (Instant bucket : expectedBuckets) {
            long tsMs = bucket.toEpochMilli();
            TimeSeriesData row = rowMap.get(bucket);
            long accepted = row != null ? row.getAccepted() : 0;
            long dropped = row != null ? row.getDropped() : 0;
            boolean isMissing = row == null;

            for (String target : targets) {
                Number value = computeMetricValue(target, accepted, dropped,
                        intervalSeconds, epsQuota, monthlyQuota, monthlyWindow, isMissing);
                List<Number> datapoint = new ArrayList<>(2);
                datapoint.add(value);
                datapoint.add(tsMs);
                targetDatapoints.get(target).add(datapoint);
            }
        }

        List<TimeseriesResponse> result = new ArrayList<>();
        for (String target : targets) {
            result.add(TimeseriesResponse.builder()
                    .target(target)
                    .datapoints(targetDatapoints.get(target))
                    .build());
        }
        return result;
    }

    // ── Private helpers ──

    private double sumHashToEps(String key) {
        try {
            long sum = redisTemplate.opsForHash().values(key).stream()
                    .mapToLong(v -> Long.parseLong(v.toString()))
                    .sum();
            return Math.round(sum / 60.0 * 10.0) / 10.0;
        } catch (Exception e) {
            log.debug("Failed to read Redis hash {}: {}", key, e.getMessage());
            return 0.0;
        }
    }

    private long getMonthlyUsed(PolicyDTO policy) {
        if (policy.getValidFrom() == null) return 0;
        long nowSec = System.currentTimeMillis() / 1000;
        long startSec = policy.getValidFrom().getEpochSecond();
        long window = Math.max(0, (nowSec - startSec) / MONTHLY_WINDOW.getSeconds());
        String key = KEY_MONTHLY + policy.getTenantId() + ":" + window;
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0;
        } catch (Exception e) {
            log.debug("Failed to read monthly quota key {}: {}", key, e.getMessage());
            return 0;
        }
    }

    private long computeMonthlyWindow(PolicyDTO policy) {
        if (policy.getValidFrom() == null) return 0;
        long nowSec = System.currentTimeMillis() / 1000;
        long startSec = policy.getValidFrom().getEpochSecond();
        return Math.max(0, (nowSec - startSec) / MONTHLY_WINDOW.getSeconds());
    }

    private Number computeMetricValue(String target, long accepted, long dropped,
                                       long intervalSec, int epsQuota, long monthlyQuota,
                                       long monthlyWindow, boolean isMissing) {
        if (isMissing) return null; // null = gap in Grafana chart

        return switch (target) {
            case "accepted_eps" -> Math.round(accepted / (double) intervalSec * 10.0) / 10.0;
            case "dropped_eps" -> Math.round(dropped / (double) intervalSec * 10.0) / 10.0;
            case "total_eps" -> Math.round((accepted + dropped) / (double) intervalSec * 10.0) / 10.0;
            case "eps_usage_pct" -> epsQuota > 0
                    ? Math.round((accepted + dropped) / (double) intervalSec / epsQuota * 1000.0) / 10.0
                    : 0.0;
            case "monthly_used", "monthly_usage_pct" -> 0L; // monthly is per-window, not per-bucket
            default -> 0.0;
        };
    }

    static long parseIntervalSeconds(String interval) {
        return switch (interval) {
            case "15s" -> 15;
            case "30s" -> 30;
            case "1m" -> 60;
            case "5m" -> 300;
            case "15m" -> 900;
            case "1h" -> 3600;
            case "6h" -> 21600;
            case "24h" -> 86400;
            default -> 60;
        };
    }

    static List<Instant> generateBuckets(Instant from, Instant to, long intervalSeconds) {
        List<Instant> buckets = new ArrayList<>();
        // Align from to the interval boundary
        long fromEpoch = from.getEpochSecond();
        long aligned = fromEpoch - (fromEpoch % intervalSeconds);
        Instant cursor = Instant.ofEpochSecond(aligned);
        Instant end = to.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        while (!cursor.isAfter(end)) {
            buckets.add(cursor);
            cursor = cursor.plusSeconds(intervalSeconds);
        }
        return buckets;
    }
}