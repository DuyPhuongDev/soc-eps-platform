package com.vdt.soc.license.dto;

import com.vdt.soc.common.core.enumeration.LicenseMode;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLicenseRequest {

    @Min(value = 1, message = "epsQuota must be at least 1")
    private Integer epsQuota;

    private LicenseMode mode;

    private Double burstMultiplier;

    private Instant startDate;

    private Instant endDate;
}