package com.vdt.soc.common.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility class for hashing operations.
 * Centralizes SHA-256 hashing used across services (API key hashing, etc.)
 */
public final class HashUtil {

    private HashUtil() {
        // Utility class
    }

    /**
     * Compute the SHA-256 hash of the given input string and return as lowercase hex.
     *
     * @param input the string to hash
     * @return the SHA-256 hash as a lowercase hex string
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}