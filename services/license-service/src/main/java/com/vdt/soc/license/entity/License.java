package com.vdt.soc.license.entity;

import com.vdt.soc.common.core.enumeration.LicenseMode;
import com.vdt.soc.common.core.enumeration.LicensePlan;
import com.vdt.soc.common.core.enumeration.LicenseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "licenses", schema = "license_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class License extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "eps_quota", nullable = false)
    private Integer epsQuota;

    @Column(name = "monthly_quota", nullable = false)
    private Long monthlyQuota;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private LicenseMode mode = LicenseMode.THROTTLE;

    @Column(name = "burst_multiplier")
    private Double burstMultiplier = 1.0;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private LicensePlan plan = LicensePlan.STARTER;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private LicenseStatus status = LicenseStatus.ACTIVE;

    // Business methods
    public boolean isExpired() {
        return Instant.now().isAfter(endDate);
    }

    public boolean isActive() {
        return status == LicenseStatus.ACTIVE && !isExpired();
    }
}
