package com.vdt.soc.aggregate.controller;

import com.vdt.soc.aggregate.dto.MetricsResponse;
import com.vdt.soc.aggregate.dto.TimeseriesResponse;
import com.vdt.soc.aggregate.metric.MetricsService;
import com.vdt.soc.common.core.enumeration.LicensePlan;
import com.vdt.soc.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = MetricsController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MetricsService metricsService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Test
    void shouldReturn200WithMetrics() throws Exception {
        MetricsResponse response = MetricsResponse.builder()
                .tenantId(tenantId)
                .epsQuota(100)
                .plan(LicensePlan.STARTER)
                .eps(MetricsResponse.EpsMetrics.builder()
                        .accepted(85.2).dropped(0).total(85.2).usagePct(85.2).build())
                .monthly(MetricsResponse.MonthlyMetrics.builder()
                        .used(4_500_000L).quota(3_000_000L).usagePct(150.0).build())
                .build();

        when(metricsService.getCurrentMetrics(tenantId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/metrics/current", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.epsQuota").value(100))
                .andExpect(jsonPath("$.plan").value("STARTER"))
                .andExpect(jsonPath("$.eps.accepted").value(85.2))
                .andExpect(jsonPath("$.eps.dropped").value(0))
                .andExpect(jsonPath("$.eps.total").value(85.2))
                .andExpect(jsonPath("$.eps.usagePct").value(85.2))
                .andExpect(jsonPath("$.monthly.used").value(4500000))
                .andExpect(jsonPath("$.monthly.quota").value(3000000))
                .andExpect(jsonPath("$.monthly.usagePct").value(150.0));
    }

    @Test
    void shouldReturn404ForUnknownTenant() throws Exception {
        when(metricsService.getCurrentMetrics(tenantId)).thenReturn(null);

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/metrics/current", tenantId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void shouldReturnGrafanaFormatTimeseries() throws Exception {
        List<TimeseriesResponse> series = List.of(
                TimeseriesResponse.builder()
                        .target("accepted_eps")
                        .datapoints(List.of(
                                List.of(85.5, 1747936800000L),
                                List.of(92.3, 1747936860000L)))
                        .build());

        when(metricsService.buildTimeseries(eq(tenantId), any(), any(), eq("1m"), any()))
                .thenReturn(series);

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/metrics/timeseries", tenantId)
                        .param("from", "2026-06-28T06:00:00Z")
                        .param("to", "2026-06-28T12:00:00Z")
                        .param("interval", "1m")
                        .param("targets", "accepted_eps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].target").value("accepted_eps"))
                .andExpect(jsonPath("$[0].datapoints", hasSize(2)))
                .andExpect(jsonPath("$[0].datapoints[0][0]").value(85.5))
                .andExpect(jsonPath("$[0].datapoints[0][1]").value(1747936800000L));
    }
}