package com.vdt.soc.tenant.config.security;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

/**
 * Authentication token populated by {@link JwtAuthenticationFilter}
 * after decoding a valid JWT.
 */
@Getter
public class JwtAuthentication extends AbstractAuthenticationToken {

    private final UUID userId;
    private final UUID tenantId;
    private final String role;
    private final String username;

    public JwtAuthentication(UUID userId, UUID tenantId, String role, String username) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.userId = userId;
        this.tenantId = tenantId;
        this.role = role;
        this.username = username;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }
}
