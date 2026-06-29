package com.vdt.soc.aggregate.metric;

import com.vdt.soc.aggregate.cache.PolicyCache;
import com.vdt.soc.aggregate.entity.TimeSeriesData;
import com.vdt.soc.aggregate.repository.TimeSeriesRepository;
import com.vdt.soc.common.core.dto.PolicyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * Snapshots Redis EPS hash data into PostgreSQL {@code timeseries_data} table.
 * Runs every 60 seconds — reads per-second hash fields from Redis,
 * aggregates into minute buckets, and upserts into the DB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSeriesService {

    private final StringRedisTemplate redisTemplate;
    private final PolicyCache policyCache;
    private final TimeSeriesRepository timeSeriesRepo;

    private static final String KEY_OK = "eps:ok:";
    private static final String KEY_DROP = "eps:drop:";

    /**
     * Snapshot Redis → PostgreSQL every 60 seconds.
     * For each active tenant: read their 1m EPS hash, aggregate by minute,
     * upsert into timeseries_data.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void snapshot() {
        log.debug("TimeSeries snapshot started");
        Instant now = Instant.now();
        // We snapshot the minute bucket that just completed
        Instant bucketMin = now.minusSeconds(60).truncatedTo(ChronoUnit.MINUTES);

        for (PolicyDTO policy : policyCache.snapshot()) {
            try {
                snapshotTenant(policy.getTenantId(), bucketMin);
            } catch (Exception e) {
                log.warn("TimeSeries snapshot failed for tenant={}: {}", policy.getTenantId(), e.getMessage());
            }
        }
        log.debug("TimeSeries snapshot completed");
    }

    private void snapshotTenant(java.util.UUID tenantId, Instant bucketMin) {
        long accepted = sumHashValues(KEY_OK + tenantId);
        long dropped = sumHashValues(KEY_DROP + tenantId);

        if (accepted == 0 && dropped == 0) {
            return; // No data to snapshot
        }

        Optional<TimeSeriesData> existing = timeSeriesRepo.findByTenantIdAndBucketMin(tenantId, bucketMin);
        if (existing.isPresent()) {
            TimeSeriesData row = existing.get();
            row.setAccepted(row.getAccepted() + accepted);
            row.setDropped(row.getDropped() + dropped);
            timeSeriesRepo.save(row);
        } else {
            timeSeriesRepo.save(TimeSeriesData.builder()
                    .tenantId(tenantId)
                    .bucketMin(bucketMin)
                    .accepted(accepted)
                    .dropped(dropped)
                    .build());
        }
    }

    private long sumHashValues(String key) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (entries.isEmpty()) return 0;
            return entries.values().stream()
                    .mapToLong(v -> Long.parseLong(v.toString()))
                    .sum();
        } catch (Exception e) {
            log.debug("Failed to read Redis hash {}: {}", key, e.getMessage());
            return 0;
        }
    }
}