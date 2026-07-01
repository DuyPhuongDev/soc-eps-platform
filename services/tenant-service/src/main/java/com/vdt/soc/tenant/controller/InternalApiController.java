package com.vdt.soc.tenant.controller;

import com.vdt.soc.common.core.dto.TenantApiKeyMapping;
import com.vdt.soc.tenant.exception.UnauthorizedException;
import com.vdt.soc.tenant.properties.InternalApiProperties;
import com.vdt.soc.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalApiController {

    private final TenantService tenantService;
    private final InternalApiProperties internalApiProperties;

    @GetMapping("/api-keys")
    public ResponseEntity<List<TenantApiKeyMapping>> listApiKeyMappings(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        verifySecret(secret);
        return ResponseEntity.ok(tenantService.listActiveApiKeyMappings());
    }

    private void verifySecret(String secret) {
        String expected = internalApiProperties.getSecret();
        if (expected == null || expected.isBlank()) {
            throw new UnauthorizedException("Internal API secret is not configured");
        }
        if (secret == null || !expected.equals(secret)) {
            throw new UnauthorizedException("Invalid internal API secret");
        }
    }
}
