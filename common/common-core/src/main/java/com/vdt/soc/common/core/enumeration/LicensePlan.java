package com.vdt.soc.common.core.enumeration;

import lombok.Getter;

/**
 * Predefined license plans — each plan bundles epsQuota, monthlyQuota,
 * mode, and burstMultiplier.
 * Tenants choose a plan instead of specifying quota/mode manually.
 * <p>
 * Quota values are designed for SIEM SOC Tier 1 use cases:
 * <ul>
 *   <li>epsQuota — events per second (rate limit)</li>
 *   <li>monthlyQuota — max events per 30-day window from license start date</li>
 * </ul>
 */
@Getter
public enum LicensePlan {

    STARTER(100, 3_000_000L, LicenseMode.THROTTLE, 1.0),
    PROFESSIONAL(500, 15_000_000L, LicenseMode.BURST_THEN_THROTTLE, 1.5),
    ENTERPRISE(2000, 60_000_000L, LicenseMode.OVERFLOW_BILLING, 2.0);

    private final int epsQuota;
    private final long monthlyQuota;
    private final LicenseMode mode;
    private final double burstMultiplier;

    LicensePlan(int epsQuota, long monthlyQuota,
                LicenseMode mode, double burstMultiplier) {
        this.epsQuota = epsQuota;
        this.monthlyQuota = monthlyQuota;
        this.mode = mode;
        this.burstMultiplier = burstMultiplier;
    }
}
