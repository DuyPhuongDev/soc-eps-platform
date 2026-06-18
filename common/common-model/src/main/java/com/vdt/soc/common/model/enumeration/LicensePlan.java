package com.vdt.soc.common.model.enumeration;

import lombok.Getter;

/**
 * Predefined license plans — each plan bundles epsQuota, mode, and burstMultiplier.
 * Tenants choose a plan instead of specifying quota/mode manually.
 */
@Getter
public enum LicensePlan {

    STARTER(100, LicenseMode.THROTTLE, 1.0),
    PROFESSIONAL(500, LicenseMode.BURST_THEN_THROTTLE, 1.5),
    ENTERPRISE(2000, LicenseMode.OVERFLOW_BILLING, 2.0);

    private final int epsQuota;
    private final LicenseMode mode;
    private final double burstMultiplier;

    LicensePlan(int epsQuota, LicenseMode mode, double burstMultiplier) {
        this.epsQuota = epsQuota;
        this.mode = mode;
        this.burstMultiplier = burstMultiplier;
    }
}