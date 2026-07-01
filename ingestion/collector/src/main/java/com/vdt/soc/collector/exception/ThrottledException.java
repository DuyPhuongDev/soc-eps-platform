package com.vdt.soc.collector.exception;

/**
 * Thrown when tenant exceeds EPS quota (token bucket empty).
 * Maps to HTTP 429 with a {@code Retry-After} header.
 */
public class ThrottledException extends RuntimeException {

    private final long retryAfterSeconds;

    public ThrottledException(String message) {
        this(message, 1);
    }

    public ThrottledException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Seconds the client should wait before retrying.
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
