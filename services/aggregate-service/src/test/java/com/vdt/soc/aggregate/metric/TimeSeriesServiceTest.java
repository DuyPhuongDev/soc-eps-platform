package com.vdt.soc.aggregate.metric;

import com.vdt.soc.aggregate.cache.PolicyCache;
import com.vdt.soc.aggregate.dto.TimeseriesResponse;
import com.vdt.soc.aggregate.entity.TimeSeriesData;
import com.vdt.soc.aggregate.repository.TimeSeriesRepository;
import com.vdt.soc.common.core.dto.PolicyDTO;
import com.vdt.soc.common.core.enumeration.LicenseMode;
import com.vdt.soc.common.core.enumeration.LicensePlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimeseriesServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private PolicyCache policyCache;

    @Mock
    private TimeSeriesRepository timeSeriesRepo;

    @InjectMocks
    private MetricsService metricsService;

    private final UUID tenantId = UUID.randomUUID();
    private final PolicyDTO policy = PolicyDTO.builder()
            .tenantId(tenantId)
            .plan(LicensePlan.STARTER)
            .epsQuota(100)
            .monthlyQuota(3_000_000L)
            .mode(LicenseMode.THROTTLE)
            .burstMultiplier(1.0)
            .validFrom(Instant.now().minusSeconds(86400))
            .build();

    @Test
    void shouldFormatGrafanaDatapointsCorrectly() {
        Instant from = Instant.parse("2026-06-28T14:00:00Z");
        Instant to = Instant.parse("2026-06-28T14:02:00Z");

        when(policyCache.get(tenantId)).thenReturn(policy);

        TimeSeriesData row = TimeSeriesData.builder()
                .tenantId(tenantId)
                .bucketMin(Instant.parse("2026-06-28T14:00:00Z"))
                .accepted(5100)  // 85 eps over 60s
                .dropped(900)    // 15 eps over 60s
                .build();

        when(timeSeriesRepo.findByTenantIdAndBucketMinBetweenOrderByBucketMinAsc(
                eq(tenantId), any(), any()))
                .thenReturn(List.of(row));

        List<TimeseriesResponse> result = metricsService.buildTimeseries(
                tenantId, from, to, "1m", List.of("accepted_eps", "dropped_eps"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTarget()).isEqualTo("accepted_eps");
        assertThat(result.get(1).getTarget()).isEqualTo("dropped_eps");

        // Check datapoints format: [value: float, timestamp_ms: long]
        TimeseriesResponse acceptedSeries = result.get(0);
        assertThat(acceptedSeries.getDatapoints()).isNotEmpty();
        List<Number> firstPoint = acceptedSeries.getDatapoints().get(0);
        assertThat(firstPoint).hasSize(2);
        assertThat(firstPoint.get(0).doubleValue()).isEqualTo(85.0); // 5100/60
        assertThat(firstPoint.get(1).longValue())
                .isEqualTo(Instant.parse("2026-06-28T14:00:00Z").toEpochMilli());
    }

    @Test
    void shouldInsertNullForMissingBuckets() {
        Instant from = Instant.parse("2026-06-28T14:00:00Z");
        Instant to = Instant.parse("2026-06-28T14:02:00Z");

        when(policyCache.get(tenantId)).thenReturn(policy);

        // No data rows in DB
        when(timeSeriesRepo.findByTenantIdAndBucketMinBetweenOrderByBucketMinAsc(
                eq(tenantId), any(), any()))
                .thenReturn(List.of());

        List<TimeseriesResponse> result = metricsService.buildTimeseries(
                tenantId, from, to, "1m", List.of("accepted_eps"));

        assertThat(result).hasSize(1);
        TimeseriesResponse series = result.get(0);
        // Should have 3 buckets (14:00, 14:01, 14:02), all null
        assertThat(series.getDatapoints()).hasSize(3);
        for (List<Number> point : series.getDatapoints()) {
            assertThat(point.get(0)).isNull(); // value is null for missing bucket
        }
    }
}