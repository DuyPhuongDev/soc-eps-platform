package com.vdt.soc.aggregate.service;

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
import java.util.Map;
import java.util.UUID;

/**
 * Snapshots Redis EPS hash data into PostgreSQL {@code timeseries_data} table.
 * Runs every 15 seconds — reads only the fields belonging to the current
 * 15-second bucket and inserts a single row. No upsert needed because
 * each bucket is processed exactly once.
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
    private static final int BUCKET_SECONDS = 15;

    /**
     * Snapshot Redis → PostgreSQL every 15 seconds.
     */
    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void snapshot() {
        log.debug("TimeSeries snapshot started");
        Instant now = Instant.now();
        long nowSec = now.getEpochSecond();

        long bucketStartSec = nowSec - (nowSec % BUCKET_SECONDS) - BUCKET_SECONDS;

        Instant bucketTime = Instant.ofEpochSecond(bucketStartSec);
        long toSec = bucketStartSec + BUCKET_SECONDS;

        for (PolicyDTO policy : policyCache.snapshot()) {
            try {
                snapshotTenant(policy.getTenantId(), bucketTime, bucketStartSec, toSec);
            } catch (Exception e) {
                log.warn("TimeSeries snapshot failed for tenant={}: {}", policy.getTenantId(), e.getMessage());
            }
        }
        log.debug("TimeSeries snapshot completed");
    }

    private void snapshotTenant(UUID tenantId, Instant bucketTime,
                                long fromSec, long toSec) {
        long accepted = sumHashFieldsInRange(KEY_OK + tenantId, fromSec, toSec);
        long dropped  = sumHashFieldsInRange(KEY_DROP + tenantId, fromSec, toSec);
        Long maxEps   = maxHashFieldInRange(KEY_OK + tenantId, fromSec, toSec);

        if (accepted == 0 && dropped == 0) {
            return;
        }

        timeSeriesRepo.save(TimeSeriesData.builder()
                .tenantId(tenantId)
                .bucketTime(bucketTime)
                .accepted(accepted)
                .dropped(dropped)
                .maxEps(maxEps != null ? maxEps : 0L)
                .build());
    }

    /**
     * Sum only the hash fields whose epoch-second key falls within [fromSec, toSec).
     */
    private long sumHashFieldsInRange(String key, long fromSec, long toSec) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (entries.isEmpty()) return 0;
            return entries.entrySet().stream()
                    .filter(e -> {
                        long sec = Long.parseLong(e.getKey().toString());
                        return sec >= fromSec && sec < toSec;
                    })
                    .mapToLong(e -> Long.parseLong(e.getValue().toString()))
                    .sum();
        } catch (Exception e) {
            log.debug("Failed to read Redis hash {}: {}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * Find the max single-second value within [fromSec, toSec).
     * Returns null if no fields in range.
     */
    private Long maxHashFieldInRange(String key, long fromSec, long toSec) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (entries.isEmpty()) return null;
            return entries.entrySet().stream()
                    .filter(e -> {
                        long sec = Long.parseLong(e.getKey().toString());
                        return sec >= fromSec && sec < toSec;
                    })
                    .map(e -> Long.parseLong(e.getValue().toString()))
                    .max(Long::compareTo)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Failed to read Redis hash {}: {}", key, e.getMessage());
            return null;
        }
    }
}
