package com.vdt.soc.common.model.enumeration;

/**
 * License enforcement mode — determines how EPS quota is enforced.
 */
public enum LicenseMode {
    THROTTLE,
    BURST_THEN_THROTTLE,
    OVERFLOW_BILLING
}