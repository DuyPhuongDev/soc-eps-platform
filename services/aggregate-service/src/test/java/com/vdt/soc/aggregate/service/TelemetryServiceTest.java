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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelemetryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private PolicyCache policyCache;
    @Mock
    private TimeSeriesRepository timeSeriesRepo;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private TelemetryService telemetryService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── 2.1 Current EPS ──

    @Test
    void getEpsCurrent_returnsSparkline() {
        long nowSec = Instant.now().getEpochSecond();
        Map<Object, Object> entries = Map.of(
                String.valueOf(nowSec - 14), "10",
                String.valueOf(nowSec - 13), "20",
                String.valueOf(nowSec - 12), "30"
        );
        when(hashOps.entries(anyString())).thenReturn(entries);

        EpsCurrentResponse response = telemetryService.getEpsCurrent(tenantId);

        assertThat(response.getAcceptedEps()).isGreaterThanOrEqualTo(0);
        assertThat(response.getAcceptedSparkline()).hasSize(15);
    }

    @Test
    void getEpsCurrent_handlesEmptyRedis() {
        when(hashOps.entries(anyString())).thenReturn(Map.of());

        EpsCurrentResponse response = telemetryService.getEpsCurrent(tenantId);

        assertThat(response.getAcceptedEps()).isEqualTo(0);
        assertThat(response.getAcceptedSparkline()).hasSize(15);
    }

    // ── 2.2 Today's Usage ──

    @Test
    void getUsageToday_returnsAcceptedSum() {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<TimeSeriesData> rows = List.of(
                TimeSeriesData.builder().tenantId(tenantId).bucketTime(startOfDay).accepted(100).dropped(10).build()
        );
        when(timeSeriesRepo.findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(
                eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(rows);

        UsageTodayResponse response = telemetryService.getUsageToday(tenantId);

        assertThat(response.getAccepted()).isEqualTo(100);
    }

    // ── 2.3 Monthly Quota ──

    @Test
    void getQuotaMonthly_returnsCorrectValues() {
        PolicyDTO policy = PolicyDTO.builder()
                .tenantId(tenantId)
                .monthlyQuota(1000L)
                .validFrom(Instant.now().minusSeconds(86400))
                .build();
        when(policyCache.get(tenantId)).thenReturn(policy);
        when(valueOps.get(anyString())).thenReturn("500");

        QuotaMonthlyResponse response = telemetryService.getQuotaMonthly(tenantId);

        assertThat(response.getUsed()).isEqualTo(500);
        assertThat(response.getTotal()).isEqualTo(1000);
        assertThat(response.getRemaining()).isEqualTo(500);
    }

    @Test
    void getQuotaMonthly_handlesZeroQuota() {
        PolicyDTO policy = PolicyDTO.builder()
                .tenantId(tenantId)
                .monthlyQuota(null)
                .validFrom(Instant.now().minusSeconds(86400))
                .build();
        when(policyCache.get(tenantId)).thenReturn(policy);
        when(valueOps.get(anyString())).thenReturn(null);

        QuotaMonthlyResponse response = telemetryService.getQuotaMonthly(tenantId);

        assertThat(response.getTotal()).isEqualTo(0);
        assertThat(response.getPercentage()).isEqualTo(0.0);
    }

    // ── 2.4 Dropped Today ──

    @Test
    void getDroppedToday_returnsDropRate() {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<TimeSeriesData> rows = List.of(
                TimeSeriesData.builder().tenantId(tenantId).bucketTime(startOfDay).accepted(100).dropped(10).build()
        );
        when(timeSeriesRepo.findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(
                eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(rows);

        DroppedTodayResponse response = telemetryService.getDroppedToday(tenantId);

        assertThat(response.getTotal()).isEqualTo(10);
        assertThat(response.getDropRate()).isEqualTo(9.09); // 10 dropped / (100+10) total * 100
    }

    @Test
    void getDroppedToday_returnsZeroWhenNoData() {
        when(timeSeriesRepo.findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(
                eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        DroppedTodayResponse response = telemetryService.getDroppedToday(tenantId);

        assertThat(response.getTotal()).isEqualTo(0);
        assertThat(response.getDropRate()).isEqualTo(0.0);
    }

    // ── 2.5 License Status ──

    @Test
    void getLicenseStatus_returnsDaysLeft() {
        Instant expiry = Instant.now().plus(Duration.ofDays(7)).plusSeconds(3600);
        PolicyDTO policy = PolicyDTO.builder().validUntil(expiry).build();
        when(policyCache.get(tenantId)).thenReturn(policy);

        LicenseStatusResponse response = telemetryService.getLicenseStatus(tenantId);

        assertThat(response.getDaysLeft()).isEqualTo(7);
        assertThat(response.getExpiryDate()).isEqualTo(expiry);
    }

    @Test
    void getLicenseStatus_returnsZeroWhenNoExpiry() {
        PolicyDTO policy = PolicyDTO.builder().validUntil(null).build();
        when(policyCache.get(tenantId)).thenReturn(policy);

        LicenseStatusResponse response = telemetryService.getLicenseStatus(tenantId);

        assertThat(response.getDaysLeft()).isEqualTo(0);
    }

    // ── 3.1 EPS History ──

    @Test
    void getEpsHistory_returnsDownsampledPoints() {
        Instant from = Instant.now().minus(Duration.ofHours(1));
        Instant to = Instant.now();
        List<TimeSeriesData> rows = List.of(
                TimeSeriesData.builder().tenantId(tenantId).bucketTime(from.plusSeconds(15)).accepted(100).dropped(10).build(),
                TimeSeriesData.builder().tenantId(tenantId).bucketTime(from.plusSeconds(30)).accepted(200).dropped(20).build()
        );
        when(timeSeriesRepo.findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(tenantId, from, to))
                .thenReturn(rows);

        List<EpsHistoryPoint> points = telemetryService.getEpsHistory(tenantId, from, to, 4);

        assertThat(points).isNotEmpty();
    }

    @Test
    void getEpsHistory_returnsEmptyWhenNoData() {
        Instant from = Instant.now().minus(Duration.ofHours(1));
        Instant to = Instant.now();
        when(timeSeriesRepo.findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(tenantId, from, to))
                .thenReturn(List.of());

        List<EpsHistoryPoint> points = telemetryService.getEpsHistory(tenantId, from, to, 4);

        assertThat(points).isEmpty();
    }

    // ── 3.2 Dropped Events ──

    @Test
    void getDroppedEventsLast24h_returnsPoints() {
        List<TimeSeriesData> rows = List.of(
                TimeSeriesData.builder().tenantId(tenantId).bucketTime(Instant.now().minusSeconds(3600)).accepted(100).dropped(10).build()
        );
        when(timeSeriesRepo.findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(
                eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(rows);

        List<DroppedEventPoint> points = telemetryService.getDroppedEventsLast24h(tenantId);

        assertThat(points).isNotEmpty();
    }

    // ── 4.1 Usage Summary ──

    @Test
    void getUsageSummary_returnsMultipleRows() {
        List<TimeSeriesData> rows = List.of(
                TimeSeriesData.builder().tenantId(tenantId).bucketTime(Instant.now()).accepted(50).dropped(5).maxEps(10L).build()
        );
        when(timeSeriesRepo.findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(
                eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(rows);

        List<UsageSummaryRow> summary = telemetryService.getUsageSummary(tenantId);

        assertThat(summary).hasSize(5); // 5m, 15m, 1h, 6h, 24h
        assertThat(summary.get(0).getPeriod()).isEqualTo("Last 5 minutes");
    }

    // ── getCurrentEps ──

    @Test
    void getCurrentEps_returnsSumOfAcceptedAndDropped() {
        when(hashOps.get(anyString(), any())).thenReturn("50");

        CurrentEpsResponse response = telemetryService.getCurrentEps(tenantId);

        assertThat(response.getEps()).isEqualTo(100); // 50 + 50
    }

    @Test
    void getCurrentEps_handlesNullRedisValue() {
        when(hashOps.get(anyString(), any())).thenReturn(null);

        CurrentEpsResponse response = telemetryService.getCurrentEps(tenantId);

        assertThat(response.getEps()).isEqualTo(0);
    }
}
