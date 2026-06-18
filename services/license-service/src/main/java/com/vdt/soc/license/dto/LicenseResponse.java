package com.vdt.soc.license.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vdt.soc.license.entity.License;
import com.vdt.soc.common.model.enumeration.LicenseMode;
import com.vdt.soc.common.model.enumeration.LicensePlan;
import com.vdt.soc.common.model.enumeration.LicenseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LicenseResponse {

    private UUID id;
    private UUID tenantId;
    private LicensePlan plan;
    private Integer epsQuota;
    private LicenseMode mode;
    private Double burstMultiplier;
    private Instant startDate;
    private Instant endDate;
    private LicenseStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static LicenseResponse from(License license) {
        return LicenseResponse.builder()
                .id(license.getId())
                .tenantId(license.getTenantId())
                .plan(license.getPlan())
                .epsQuota(license.getEpsQuota())
                .mode(license.getMode())
                .burstMultiplier(license.getBurstMultiplier())
                .startDate(license.getStartDate())
                .endDate(license.getEndDate())
                .status(license.getStatus())
                .createdAt(license.getCreatedAt())
                .updatedAt(license.getUpdatedAt())
                .build();
    }
}