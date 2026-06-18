package com.vdt.soc.collector.exception;

/**
 * Thrown when API key is invalid or not found in cache.
 * Maps to HTTP 401.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
