package com.vdt.soc.collector.exception;

/**
 * Thrown when tenant exceeds daily or monthly event volume quota.
 * Distinct from {@link ThrottledException} (EPS rate limit).
 * Maps to HTTP 429 with status {@code quota_exceeded}.
 */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(String message) {
        super(message);
    }
}
