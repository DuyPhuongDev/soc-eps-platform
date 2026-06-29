package com.vdt.soc.aggregate.controller;

import com.vdt.soc.aggregate.dto.DropStatsResponse;
import com.vdt.soc.aggregate.metric.DropStatsService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = StatsController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DropStatsService dropStatsService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Test
    void shouldReturnDropStats() throws Exception {
        DropStatsResponse response = DropStatsResponse.builder()
                .tenantId(tenantId)
                .period("day")
                .total(12500)
                .buckets(List.of(
                        DropStatsResponse.Bucket.builder().count(500).build(),
                        DropStatsResponse.Bucket.builder().count(800).build()))
                .build();

        when(dropStatsService.getDropStats(tenantId, "day")).thenReturn(response);

        mockMvc.perform(get("/api/v1/tenants/{tenantId}/stats/dropped", tenantId)
                        .param("period", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.period").value("day"))
                .andExpect(jsonPath("$.total").value(12500))
                .andExpect(jsonPath("$.buckets.length()").value(2));
    }
}