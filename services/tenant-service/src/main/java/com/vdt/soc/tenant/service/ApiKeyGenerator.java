package com.vdt.soc.tenant.service;

import com.vdt.soc.tenant.properties.ApiKeyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class ApiKeyGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyProperties properties;

    public String generateRawKey() {
        byte[] randomBytes = new byte[properties.getRandomBytes()];
        SECURE_RANDOM.nextBytes(randomBytes);
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return properties.getPrefix() + body;
    }

    public String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
