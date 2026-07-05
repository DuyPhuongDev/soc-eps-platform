package com.vdt.soc.notification.client;

import com.vdt.soc.notification.dto.TenantContactResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Feign client to fetch tenant contact information from tenant-service.
 * The target endpoint is gated by {@code X-Internal-Secret} (injected
 * via {@code FeignConfig}).
 */
@FeignClient(name = "tenant-service")
public interface TenantClient {

    @GetMapping("/api/v1/internal/tenants/{id}")
    TenantContactResponse getById(@PathVariable("id") UUID id);
}
