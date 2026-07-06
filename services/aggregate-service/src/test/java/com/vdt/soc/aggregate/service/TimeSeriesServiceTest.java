package com.vdt.soc.aggregate.service;

import com.vdt.soc.aggregate.cache.PolicyCache;
import com.vdt.soc.aggregate.entity.TimeSeriesData;
import com.vdt.soc.aggregate.repository.TimeSeriesRepository;
import com.vdt.soc.common.core.dto.PolicyDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimeSeriesServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private PolicyCache policyCache;
    @Mock
    private TimeSeriesRepository timeSeriesRepo;
    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @InjectMocks
    private TimeSeriesService timeSeriesService;

    private final UUID tenantId = UUID.randomUUID();
    private long bucketStartSec;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        long nowSec = Instant.now().getEpochSecond();
        bucketStartSec = nowSec - (nowSec % 15) - 15;
    }

    @Test
    void snapshot_persistsDataForActivePolicies() {
        PolicyDTO policy = PolicyDTO.builder().tenantId(tenantId).build();
        when(policyCache.snapshot()).thenReturn(java.util.List.of(policy));

        Map<Object, Object> entries = Map.of(
                String.valueOf(bucketStartSec), "10",
                String.valueOf(bucketStartSec + 1), "20"
        );
        when(hashOps.entries(anyString())).thenReturn(entries);

        timeSeriesService.snapshot();

        verify(timeSeriesRepo).save(any(TimeSeriesData.class));
    }

    @Test
    void snapshot_skipsWhenNoPolicies() {
        when(policyCache.snapshot()).thenReturn(java.util.List.of());

        timeSeriesService.snapshot();

        verify(timeSeriesRepo, never()).save(any(TimeSeriesData.class));
    }

    @Test
    void snapshot_skipsWhenZeroAcceptedAndDropped() {
        PolicyDTO policy = PolicyDTO.builder().tenantId(tenantId).build();
        when(policyCache.snapshot()).thenReturn(java.util.List.of(policy));
        when(hashOps.entries(anyString())).thenReturn(Map.of());

        timeSeriesService.snapshot();

        verify(timeSeriesRepo, never()).save(any(TimeSeriesData.class));
    }

    @Test
    void sumHashFieldsInRange_sumsCorrectly() {
        PolicyDTO policy = PolicyDTO.builder().tenantId(tenantId).build();
        when(policyCache.snapshot()).thenReturn(java.util.List.of(policy));

        Map<Object, Object> entries = Map.of(
                String.valueOf(bucketStartSec), "10",
                String.valueOf(bucketStartSec + 1), "20",
                String.valueOf(bucketStartSec + 2), "30"
        );
        when(hashOps.entries(anyString())).thenReturn(entries);

        timeSeriesService.snapshot();

        verify(timeSeriesRepo).save(any(TimeSeriesData.class));
    }

    @Test
    void maxHashFieldInRange_findsMax() {
        PolicyDTO policy = PolicyDTO.builder().tenantId(tenantId).build();
        when(policyCache.snapshot()).thenReturn(java.util.List.of(policy));

        Map<Object, Object> entries = Map.of(
                String.valueOf(bucketStartSec), "50",
                String.valueOf(bucketStartSec + 1), "100",
                String.valueOf(bucketStartSec + 2), "75"
        );
        when(hashOps.entries(anyString())).thenReturn(entries);

        timeSeriesService.snapshot();

        verify(timeSeriesRepo).save(any(TimeSeriesData.class));
    }
}
