package com.vdt.soc.common.security;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

/**
 * JWT-based authentication token used across all services.
 * Consolidated from duplicated classes in tenant-service and license-service.
 */
@Getter
public class JwtAuthentication extends AbstractAuthenticationToken {

    private final JwtPrincipal jwtPrincipal;

    public JwtAuthentication(UUID userId, UUID tenantId, String username, String role) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.jwtPrincipal = new JwtPrincipal(userId, tenantId, username, role);
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return jwtPrincipal;
    }

    public String getUsername() {
        return this.jwtPrincipal.username();
    }

    public UUID getTenantId(){
        return this.jwtPrincipal.tenantId();
    }

    public UUID getUserId(){
        return this.jwtPrincipal.userId();
    }
}