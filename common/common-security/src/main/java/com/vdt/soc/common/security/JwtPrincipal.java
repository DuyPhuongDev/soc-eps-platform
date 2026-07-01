package com.vdt.soc.common.security;

import java.util.UUID;

public record JwtPrincipal(
        UUID userId,
        UUID tenantId,
        String username,
        String role
) {
}