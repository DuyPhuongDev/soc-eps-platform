package com.vdt.soc.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * JWT verification utility shared across all services.
 * Token generation remains in the tenant service (the issuer).
 * <p>
 * This class must be registered as a bean in each service's configuration.
 * For services that only verify tokens (no generation), register directly:
 * <pre>
 *     &#64;Bean
 *     public JwtUtil jwtUtil(JwtProperties properties) {
 *         return new JwtUtil(properties);
 *     }
 * </pre>
 * For the tenant service that also generates tokens, use the subclass:
 * <pre>
 *     &#64;Bean
 *     public JwtUtil jwtUtil(JwtProperties properties) {
 *         return new JwtUtil(properties); // or the tenant subclass
 *     }
 * </pre>
 */
public class JwtUtil {

    public static final String CLAIM_TENANT_ID = "tenantId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_USERNAME = "username";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        if (properties.getSecret() == null || properties.getSecret().length() < 32) {
            throw new IllegalStateException("app.jwt.secret must be configured with at least 32 characters");
        }
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token);
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public UUID extractTenantId(Claims claims) {
        String tenantId = claims.get(CLAIM_TENANT_ID, String.class);
        return tenantId == null ? null : UUID.fromString(tenantId);
    }

    public String extractRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    public String extractUsername(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }

    public long getExpirationSeconds() {
        return properties.getExpirationSeconds();
    }

    protected JwtProperties getProperties() {
        return properties;
    }

    protected SecretKey getSigningKey() {
        return signingKey;
    }
}