package com.vdt.soc.aggregate.metric;

import com.vdt.soc.aggregate.cache.PolicyCache;
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
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricsServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private PolicyCache policyCache;

    @Mock
    private TimeSeriesRepository timeSeriesRepo;

    @Mock
    @SuppressWarnings("rawtypes")
    private HashOperations hashOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

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
    @SuppressWarnings("unchecked")
    void shouldCalculateAcceptedEpsFromRedisHash() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.values("eps:ok:" + tenantId))
                .thenReturn(List.of("1000", "2000", "3000"));
        when(hashOperations.values("eps:drop:" + tenantId))
                .thenReturn(List.of());
        when(valueOperations.get(anyString())).thenReturn("1000000");
        when(policyCache.get(tenantId)).thenReturn(policy);

        var response = metricsService.getCurrentMetrics(tenantId);

        assertThat(response).isNotNull();
        assertThat(response.getEps().getAccepted()).isEqualTo(100.0); // 6000/60
        assertThat(response.getEps().getDropped()).isEqualTo(0.0);
        assertThat(response.getEps().getTotal()).isEqualTo(100.0);
        assertThat(response.getEps().getUsagePct()).isEqualTo(100.0); // 100/100
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnZeroWhenNoData() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.values("eps:ok:" + tenantId)).thenReturn(List.of());
        when(hashOperations.values("eps:drop:" + tenantId)).thenReturn(List.of());
        when(valueOperations.get(anyString())).thenReturn("0");
        when(policyCache.get(tenantId)).thenReturn(policy);

        var response = metricsService.getCurrentMetrics(tenantId);

        assertThat(response).isNotNull();
        assertThat(response.getEps().getAccepted()).isEqualTo(0.0);
        assertThat(response.getEps().getDropped()).isEqualTo(0.0);
        assertThat(response.getMonthly().getUsed()).isEqualTo(0);
    }

    @Test
    void shouldReturnNullForUnknownTenant() {
        when(policyCache.get(tenantId)).thenReturn(PolicyDTO.DEFAULT);

        var response = metricsService.getCurrentMetrics(tenantId);

        assertThat(response).isNull();
    }

    @Test
    void shouldParseIntervalSeconds() {
        assertThat(MetricsService.parseIntervalSeconds("1m")).isEqualTo(60);
        assertThat(MetricsService.parseIntervalSeconds("5m")).isEqualTo(300);
        assertThat(MetricsService.parseIntervalSeconds("15m")).isEqualTo(900);
        assertThat(MetricsService.parseIntervalSeconds("1h")).isEqualTo(3600);
        assertThat(MetricsService.parseIntervalSeconds("unknown")).isEqualTo(60);
    }

    @Test
    void shouldGenerateBucketsCorrectly() {
        Instant from = Instant.parse("2026-06-28T14:00:00Z");
        Instant to = Instant.parse("2026-06-28T14:05:00Z");

        List<Instant> buckets = MetricsService.generateBuckets(from, to, 60);

        assertThat(buckets).hasSize(6);
        assertThat(buckets.get(0)).isEqualTo(Instant.parse("2026-06-28T14:00:00Z"));
        assertThat(buckets.get(5)).isEqualTo(Instant.parse("2026-06-28T14:05:00Z"));
    }
}