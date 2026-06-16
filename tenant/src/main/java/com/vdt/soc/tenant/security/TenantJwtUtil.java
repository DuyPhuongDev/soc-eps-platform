package com.vdt.soc.tenant.security;

import com.vdt.soc.common.security.JwtProperties;
import com.vdt.soc.common.security.JwtUtil;
import com.vdt.soc.tenant.entity.User;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

/**
 * Tenant-specific JWT utility that adds token generation on top of
 * the shared verification logic in {@link JwtUtil}.
 */
@Component
public class TenantJwtUtil extends JwtUtil {

    public TenantJwtUtil(JwtProperties properties) {
        super(properties);
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(getExpirationSeconds());

        var builder = Jwts.builder()
                .issuer(getProperties().getIssuer())
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole().name());

        if (user.getTenantId() != null) {
            builder.claim(CLAIM_TENANT_ID, user.getTenantId().toString());
        }

        return builder.signWith(getSigningKey(), Jwts.SIG.HS256).compact();
    }
}