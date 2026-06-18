package com.vdt.soc.license.exception;

import java.util.UUID;

public class LicenseNotFoundException extends RuntimeException {
    public LicenseNotFoundException(UUID licenseId) {
        super("License not found: " + licenseId);
    }
}