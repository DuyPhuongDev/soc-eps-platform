package com.vdt.soc.aggregate.service;

import com.vdt.soc.aggregate.cache.PolicyCache;
import com.vdt.soc.aggregate.dto.CurrentEpsResponse;
import com.vdt.soc.aggregate.dto.DroppedEventPoint;
import com.vdt.soc.aggregate.dto.DroppedTodayResponse;
import com.vdt.soc.aggregate.dto.EpsCurrentResponse;
import com.vdt.soc.aggregate.dto.EpsHistoryPoint;
import com.vdt.soc.aggregate.dto.LicenseStatusResponse;
import com.vdt.soc.aggregate.dto.QuotaMonthlyResponse;
import com.vdt.soc.aggregate.dto.UsageSummaryRow;
import com.vdt.soc.aggregate.dto.UsageTodayResponse;
import com.vdt.soc.aggregate.entity.TimeSeriesData;
import com.vdt.soc.aggregate.repository.TimeSeriesRepository;
import com.vdt.soc.common.core.dto.PolicyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final StringRedisTemplate redisTemplate;
    private final PolicyCache policyCache;
    private final TimeSeriesRepository timeSeriesRepo;

    static final String KEY_OK = "eps:ok:";
    static final String KEY_DROP = "eps:drop:";
    static final String KEY_MONTHLY = "quota:monthly:";
    static final Duration MONTHLY_WINDOW = Duration.ofDays(30);

    private static final long MIN_BUCKET_MS = 15_000;

    // ── 2.1 Current EPS ────────────────────────────────────────────

    public EpsCurrentResponse getEpsCurrent(UUID tenantId) {
        long nowSec = Instant.now().getEpochSecond();

        List<Long> acceptedSeconds = readLastNSeconds(KEY_OK + tenantId, nowSec, 15);
        List<Long> droppedSeconds = readLastNSeconds(KEY_DROP + tenantId, nowSec, 15);

        return EpsCurrentResponse.builder()
                .acceptedEps(avgLastN(acceptedSeconds, 15))
                .droppedEps(avgLastN(droppedSeconds, 15))
                .acceptedSparkline(toIntList(acceptedSeconds))
                .droppedSparkline(toIntList(droppedSeconds))
                .build();
    }

    // ── 2.2 Today's Usage ──────────────────────────────────────────

    public UsageTodayResponse getUsageToday(UUID tenantId) {
        Instant startOfDay = startOfToday();

        long accepted = sumAcceptedBetween(tenantId, startOfDay, Instant.now());

        return UsageTodayResponse.builder()
                .accepted(accepted).build();
    }

    // ── 2.3 Monthly Quota ──────────────────────────────────────────

    public QuotaMonthlyResponse getQuotaMonthly(UUID tenantId) {
        PolicyDTO policy = policyCache.get(tenantId);
        long total = policy.getMonthlyQuota() != null ? policy.getMonthlyQuota() : 0;

        long used = getMonthlyUsed(policy);
        long remaining = Math.max(0, total - used);
        double pct = total > 0
                ? Math.round((double) used / total * 10000.0) / 100.0 : 0.0;

        Instant reset = computeNextReset(policy);

        return QuotaMonthlyResponse.builder()
                .used(used).total(total).remaining(remaining)
                .percentage(pct)
                .resetDate(reset)
                .daysRemaining((int) Duration.between(Instant.now(), reset).toDays())
                .build();
    }

    // ── 2.4 Dropped Today ──────────────────────────────────────────

    public DroppedTodayResponse getDroppedToday(UUID tenantId) {
        Instant startOfDay = startOfToday();
        Instant now = Instant.now();

        long totalDropped = sumDroppedBetween(tenantId, startOfDay, now);
        long totalAccepted = sumAcceptedBetween(tenantId, startOfDay, now);
        long total = totalAccepted + totalDropped;
        double dropRate = total > 0
                ? Math.round((double) totalDropped / total * 10000.0) / 100.0 : 0.0;

        return DroppedTodayResponse.builder()
                .total(totalDropped)
                .sparkline(buildSparkline(tenantId, startOfDay, now, 12))
                .dropRate(dropRate)
                .build();
    }

    // ── 2.5 License Status ─────────────────────────────────────────

    public LicenseStatusResponse getLicenseStatus(UUID tenantId) {
        PolicyDTO policy = policyCache.get(tenantId);
        int daysLeft = 0;
        Instant expiryDate = null;
        if (policy.getValidUntil() != null) {
            daysLeft = (int) Duration.between(Instant.now(), policy.getValidUntil()).toDays();
            expiryDate = policy.getValidUntil();
        }
        return LicenseStatusResponse.builder()
                .daysLeft(daysLeft).expiryDate(expiryDate).build();
    }

    // ── 3.1 EPS History ────────────────────────────────────────────

    public List<EpsHistoryPoint> getEpsHistory(UUID tenantId, Instant from, Instant to, int maxDataPoints) {

        List<TimeSeriesData> rows = timeSeriesRepo
                .findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(tenantId, from, to);

        if (rows.isEmpty()) return List.of();

        int points = Math.max(1, maxDataPoints);

        long totalDurationMs = Duration.between(from, to).toMillis();
        long bucketMs = Math.max(1000, totalDurationMs / points);

        long effectiveBucketMs = roundUpBucket(bucketMs);
        double effectiveBucketSec = effectiveBucketMs / 1000.0;

        return downsample(rows, from, effectiveBucketMs, points,
                (bStart, acc, drop, count) -> {
                    int acceptedEps = count > 0 ? (int) Math.round(acc / effectiveBucketSec) : 0;
                    int droppedEps = count > 0 ? (int) Math.round(drop / effectiveBucketSec) : 0;

                    return EpsHistoryPoint.builder()
                            .time(Instant.ofEpochMilli(bStart))
                            .accepted(acceptedEps)
                            .dropped(droppedEps)
                            .build();
                });
    }

    // ── 3.2 Dropped Events ─────────────────────────────────────────

    public List<DroppedEventPoint> getDroppedEventsLast24h(UUID tenantId) {

        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofHours(24));

        List<TimeSeriesData> rows = timeSeriesRepo
                .findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(tenantId, from, now);
        if (rows.isEmpty()) return List.of();

        long totalDurationMs = Duration.ofHours(24).toMillis();

        int totalPoints = 8;
        long rawBucketMs = totalDurationMs / totalPoints;

        long bucketMs = roundUpBucket(rawBucketMs);

        return downsample(rows, from, bucketMs, totalPoints,
                (bStart, acc, drop, count) -> DroppedEventPoint.builder()
                        .time(Instant.ofEpochMilli(bStart))
                        .dropped(drop)
                        .build());
    }

    // ── 4.1 Usage Summary ──────────────────────────────────────────

    public List<UsageSummaryRow> getUsageSummary(UUID tenantId) {
        Instant now = Instant.now();
        return List.of(
                buildSummaryRow(tenantId, "Last 5 minutes", now.minus(5, ChronoUnit.MINUTES), now),
                buildSummaryRow(tenantId, "Last 15 minutes", now.minus(15, ChronoUnit.MINUTES), now),
                buildSummaryRow(tenantId, "Last 1 hour", now.minus(1, ChronoUnit.HOURS), now),
                buildSummaryRow(tenantId, "Last 6 hours", now.minus(6, ChronoUnit.HOURS), now),
                buildSummaryRow(tenantId, "Last 24 hours", now.minus(24, ChronoUnit.HOURS), now)
        );
    }

    public CurrentEpsResponse getCurrentEps(UUID tenantId) {
        long lastSecond = Instant.now().getEpochSecond()-1;
        long currentAcceptEps = readLastSecond(KEY_OK + tenantId, lastSecond);
        long currentDroppedEps = readLastSecond(KEY_DROP + tenantId, lastSecond);
        return new CurrentEpsResponse(currentAcceptEps+currentDroppedEps);
    }

    // ── Private helpers ────────────────────────────────────────────
    private long readLastSecond(String redisKey, long nowSec) {
        try {
            Object value = redisTemplate.opsForHash()
                    .get(redisKey, String.valueOf(nowSec));

            return value != null ? Long.parseLong(value.toString()) : 0L;
        } catch (Exception e) {
            log.debug("Failed to read Redis hash {}: {}", redisKey, e.getMessage());
            return 0L;
        }
    }

    private static long roundUpBucket(long bucketMs) {
        if (bucketMs < MIN_BUCKET_MS) return MIN_BUCKET_MS;
        long reminder = bucketMs % MIN_BUCKET_MS;
        if (reminder == 0) return bucketMs;
        return bucketMs + MIN_BUCKET_MS - reminder;
    }

    private List<Long> readLastNSeconds(String redisKey, long nowSec, int n) {
        List<Long> result = new ArrayList<>(n);
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(redisKey);
            for (int i = n - 1; i >= 0; i--) {
                Object val = entries.get(String.valueOf(nowSec - i));
                result.add(val != null ? Long.parseLong(val.toString()) : 0L);
            }
        } catch (Exception e) {
            log.debug("Failed to read Redis hash {}: {}", redisKey, e.getMessage());
            for (int i = 0; i < n; i++) result.add(0L);
        }
        return result;
    }

    private static int avgLastN(List<Long> values, int n) {
        if (values.isEmpty()) return 0;
        return (int) Math.round(values.stream().mapToLong(Long::longValue).sum() / (double) n);
    }

    private static List<Integer> toIntList(List<Long> source) {
        return source.stream().map(Long::intValue).toList();
    }

    private long sumAcceptedBetween(UUID tenantId, Instant from, Instant to) {
        return timeSeriesRepo
                .findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(tenantId, from, to)
                .stream().mapToLong(TimeSeriesData::getAccepted).sum();
    }

    private long sumDroppedBetween(UUID tenantId, Instant from, Instant to) {
        return timeSeriesRepo
                .findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(tenantId, from, to)
                .stream().mapToLong(TimeSeriesData::getDropped).sum();
    }

    private List<Long> buildSparkline(UUID tenantId, Instant from, Instant to, int points) {
        List<TimeSeriesData> rows = timeSeriesRepo
                .findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(tenantId, from, to);
        long bucketMs = roundUpBucket(Duration.between(from, to).toMillis() / points);
        List<Long> sparkline = new ArrayList<>(points);
        int idx = 0;
        for (int i = 0; i < points; i++) {
            long end = from.toEpochMilli() + (i + 1) * bucketMs;
            long sum = 0;
            while (idx < rows.size() && rows.get(idx).getBucketTime().toEpochMilli() < end) {
                sum += rows.get(idx).getDropped();
                idx++;
            }
            sparkline.add(sum);
        }
        return sparkline;
    }

    private <T> List<T> downsample(List<TimeSeriesData> rows, Instant from,
                                   long bucketMs, int targetPoints, PointBuilder<T> builder) {
        List<T> result = new ArrayList<>(targetPoints);
        int idx = 0;

        for (int i = 0; i < targetPoints; i++) {
            long bStart = from.toEpochMilli() + i * bucketMs;
            long bEnd = bStart + bucketMs;

            long acc = 0;
            long drop = 0;
            int count = 0;

            while (idx < rows.size() && rows.get(idx).getBucketTime().toEpochMilli() < bEnd) {
                if (rows.get(idx).getBucketTime().toEpochMilli() >= bStart) {
                    acc += rows.get(idx).getAccepted();
                    drop += rows.get(idx).getDropped();
                    count++;
                }
                idx++;
            }

            result.add(builder.build(bStart, acc, drop, count));
        }
        return result;
    }

    private static Instant computeNextReset(PolicyDTO policy) {
        if (policy.getValidFrom() == null) {
            return YearMonth.now(ZoneOffset.UTC).plusMonths(1)
                    .atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        long windowSec = MONTHLY_WINDOW.getSeconds();
        long startSec = policy.getValidFrom().getEpochSecond();
        long currentWindow = (Instant.now().getEpochSecond() - startSec) / windowSec;
        return Instant.ofEpochSecond(startSec + (currentWindow + 1) * windowSec);
    }

    private static Instant startOfToday() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private UsageSummaryRow buildSummaryRow(UUID tenantId, String label,
                                            Instant from, Instant to) {
        List<TimeSeriesData> rows = timeSeriesRepo
                .findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(tenantId, from, to);

        long accepted = rows.stream().mapToLong(TimeSeriesData::getAccepted).sum();
        long dropped = rows.stream().mapToLong(TimeSeriesData::getDropped).sum();

        int maxEps = rows.stream()
                .map(TimeSeriesData::getMaxEps).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).map(Long::intValue).orElse(0);


        double dropRate = accepted > 0
                ? Math.round((double) dropped / accepted * 10000.0) / 100.0 : 0.0;

        return UsageSummaryRow.builder()
                .period(label).accepted(accepted).dropped(dropped)
                .maxEps(maxEps).dropRate(dropRate).build();
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
}
