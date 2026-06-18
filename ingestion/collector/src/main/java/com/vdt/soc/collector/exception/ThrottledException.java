package com.vdt.soc.collector.exception;

/**
 * Thrown when tenant exceeds EPS quota (token bucket empty).
 * Maps to HTTP 429.
 */
public class ThrottledException extends RuntimeException {

    public ThrottledException(String message) {
        super(message);
    }
}
