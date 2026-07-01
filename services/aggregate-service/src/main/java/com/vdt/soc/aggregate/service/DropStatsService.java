package com.vdt.soc.aggregate.service;

import com.vdt.soc.aggregate.dto.DropStatsResponse;
import com.vdt.soc.aggregate.entity.TimeSeriesData;
import com.vdt.soc.aggregate.repository.TimeSeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DropStatsService {

    private final StringRedisTemplate redisTemplate;
    private final TimeSeriesRepository timeSeriesRepo;

    private static final String KEY_DROP = "eps:drop:";

    public DropStatsResponse getDropStats(UUID tenantId, String period) {
        long total = 0;
        long peakCount = 0;
        Instant peakHour = null;
        List<DropStatsResponse.Bucket> buckets = new ArrayList<>();

        switch (period) {
            case "day" -> {
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
            case "week" -> {
                Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
                List<TimeSeriesData> rows = timeSeriesRepo
                        .findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(
                                tenantId, from, Instant.now());
                for (TimeSeriesData row : rows) {
                    total += row.getDropped();
                    if (row.getDropped() > peakCount) {
                        peakCount = row.getDropped();
                        peakHour = row.getBucketTime();
                    }
                    buckets.add(DropStatsResponse.Bucket.builder()
                            .timestamp(row.getBucketTime())
                            .count(row.getDropped())
                            .build());
                }
            }
            case "month" -> {
                Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
                List<TimeSeriesData> rows = timeSeriesRepo
                        .findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(
                                tenantId, from, Instant.now());
                // Aggregate by day for monthly view
                Map<Instant, Long> daily = rows.stream()
                        .collect(Collectors.groupingBy(
                                r -> r.getBucketTime().truncatedTo(ChronoUnit.DAYS),
                                Collectors.summingLong(TimeSeriesData::getDropped)));
                for (Map.Entry<Instant, Long> entry : daily.entrySet()) {
                    total += entry.getValue();
                    if (entry.getValue() > peakCount) {
                        peakCount = entry.getValue();
                        peakHour = entry.getKey();
                    }
                    buckets.add(DropStatsResponse.Bucket.builder()
                            .timestamp(entry.getKey())
                            .count(entry.getValue())
                            .build());
                }
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

    private Map<Instant, Long> aggregateByHour(UUID tenantId) {
        try {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(KEY_DROP + tenantId);
            return fields.entrySet().stream()
                    .collect(Collectors.groupingBy(
                            e -> Instant.ofEpochSecond(Long.parseLong(e.getKey().toString()))
                                    .truncatedTo(ChronoUnit.HOURS),
                            Collectors.summingLong(e -> Long.parseLong(e.getValue().toString()))));
        } catch (Exception e) {
            log.debug("Failed to read drop stats for tenant {}: {}", tenantId, e.getMessage());
            return Map.of();
        }
    }
}
