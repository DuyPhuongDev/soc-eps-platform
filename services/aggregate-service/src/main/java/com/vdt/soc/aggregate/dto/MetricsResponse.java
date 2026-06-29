package com.vdt.soc.aggregate.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vdt.soc.common.core.enumeration.LicensePlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetricsResponse {

    private UUID tenantId;
    private Integer epsQuota;
    private LicensePlan plan;
    private EpsMetrics eps;
    private MonthlyMetrics monthly;

    @Builder
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EpsMetrics {
        private double accepted;
        private double dropped;
        private double total;
        private double usagePct;
    }

    @Builder
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MonthlyMetrics {
        private long used;
        private long quota;
        private double usagePct;
    }
}