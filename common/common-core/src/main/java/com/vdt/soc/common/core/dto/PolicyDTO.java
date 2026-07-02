package com.vdt.soc.common.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vdt.soc.common.core.enumeration.LicenseMode;
import com.vdt.soc.common.core.enumeration.LicensePlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Cached policy from license-service.
 * Single source of truth — used by license-service (publisher),
 * collector-service (consumer via etcd), and metric-aggregator.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolicyDTO {

    /**
     * Default policy for unknown tenants — throttle everything.
     */
    public static final PolicyDTO DEFAULT = PolicyDTO.builder()
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .plan(LicensePlan.STARTER)
            .epsQuota(0)
            .monthlyQuota(0L)
            .mode(LicenseMode.THROTTLE)
            .burstMultiplier(1.0)
            .validUntil(null)
            .build();
    private UUID tenantId;
    private LicensePlan plan;
    private Integer epsQuota;
    private Long monthlyQuota;
    private LicenseMode mode;
    private Double burstMultiplier;
    private Instant validFrom;
    private Instant validUntil;

    public boolean isDefault() {
        return epsQuota == 0;
    }
}
