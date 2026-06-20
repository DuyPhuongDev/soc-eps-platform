package com.vdt.soc.license.dto;

import com.vdt.soc.common.core.enumeration.LicensePlan;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLicenseRequest {

    @NotNull(message = "tenantId is required")
    private UUID tenantId;

    @NotNull(message = "plan is required")
    private LicensePlan plan;

    @NotNull(message = "startDate is required")
    private Instant startDate;

    @NotNull(message = "endDate is required")
    private Instant endDate;
}