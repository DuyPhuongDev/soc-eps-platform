package com.vdt.soc.aggregate.metric;

import com.vdt.soc.aggregate.dto.DropStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates dropped event statistics from Redis snapshots.
 * Drop stats are persisted by the hourly rollup job into Redis keys:
 * stats:drop:daily:{t}:{yyyyMMdd}, stats:drop:weekly:{t}:{yyyyWww}, stats:drop:monthly:{t}:{yyyyMM}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DropStatsService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_DROP = "eps:drop:";
    private static final String KEY_STATS_DROP_DAILY = "stats:drop:daily:";
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    /**
     * Get dropped event stats for a tenant.
     *
     * @param tenantId tenant UUID
     * @param period   "day", "week", or "month"
     */
    public DropStatsResponse getDropStats(UUID tenantId, String period) {
        long total = 0;
        long peakCount = 0;
        Instant peakHour = null;
        List<DropStatsResponse.Bucket> buckets = new ArrayList<>();

        switch (period) {
            case "day" -> {
                // Query the live 1d drop hash (last 24h) — aggregate by hour
                Map<Instant, Long> hourly = aggregateByHour(tenantId);
                for (Map.Entry<Instant, Long> entry : hourly.entrySet()) {
                    long count = entry.getValue();
                    total += count;
                    if (count > peakCount) {
                        peakCount = count;
                        peakHour = entry.getKey();
                    }
                    buckets.add(DropStatsResponse.Bucket.builder()
                            .timestamp(entry.getKey())
                            .count(count)
                            .build());
                }
            }
            case "week", "month" -> {
                // Read from daily snapshots (persisted by rollup job)
                String today = DAY_FMT.format(Instant.now());
                String key = KEY_STATS_DROP_DAILY + tenantId + ":" + today;
                String value = redisTemplate.opsForValue().get(key);
                total = value != null ? Long.parseLong(value) : 0;
                buckets.add(DropStatsResponse.Bucket.builder()
                        .timestamp(Instant.now().truncatedTo(ChronoUnit.DAYS))
                        .count(total)
                        .build());
            }
        }

        return DropStatsResponse.builder()
                .tenantId(tenantId)
                .period(period)
                .total(total)
                .peakHour(peakHour)
                .peakCount(peakCount)
                .buckets(buckets)
                .build();
    }

    /**
     * Aggregate the 1d drop hash into hourly buckets.
     */
    private Map<Instant, Long> aggregateByHour(UUID tenantId) {
        String key = KEY_DROP + tenantId;
        try {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
            return fields.entrySet().stream()
                    .collect(Collectors.groupingBy(
                            e -> Instant.ofEpochSecond(Long.parseLong(e.getKey().toString()))
                                    .truncatedTo(ChronoUnit.HOURS),
                            Collectors.summingLong(e -> Long.parseLong(e.getValue().toString()))
                    ));
        } catch (Exception e) {
            log.debug("Failed to read drop stats for tenant {}: {}", tenantId, e.getMessage());
            return Map.of();
        }
    }
}