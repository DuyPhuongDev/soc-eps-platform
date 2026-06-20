package com.vdt.soc.license.controller;

import com.vdt.soc.common.core.dto.PolicyDTO;
import com.vdt.soc.common.security.JwtPrincipal;
import com.vdt.soc.license.dto.CreateLicenseRequest;
import com.vdt.soc.license.dto.LicenseAuditLogResponse;
import com.vdt.soc.license.dto.LicenseResponse;
import com.vdt.soc.license.dto.PageResponse;
import com.vdt.soc.license.dto.UpdateLicenseRequest;
import com.vdt.soc.license.service.LicenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/licenses")
@RequiredArgsConstructor
@Tag(name = "License", description = "License management APIs")
public class LicenseController {

    private final LicenseService licenseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new license", description = "SYSTEM_ADMIN only. Only one active license per tenant at a time.")
    public LicenseResponse createLicense(@Valid @RequestBody CreateLicenseRequest request,
                                         @AuthenticationPrincipal JwtPrincipal principal) {
        return licenseService.createLicense(request, principal.username());
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "List licenses", description = "SYSTEM_ADMIN sees all. TENANT_ADMIN/TENANT_VIEWER see their tenant's licenses.")
    public ResponseEntity<PageResponse<LicenseResponse>> listAllLicense(
            @RequestParam(required = false) @Parameter(description = "Filter by tenant ID") UUID tenantId,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        if (tenantId != null) {
            return ResponseEntity.ok(licenseService.listLicenses(tenantId, pageable));
        }
        return ResponseEntity.ok(licenseService.listAllLicenses(pageable));
    }

    @GetMapping("/my-license")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_VIEWER')")
    @Operation(summary = "List licenses of current tenant")
    public ResponseEntity<PageResponse<LicenseResponse>> listMyLicenses(
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(licenseService.listLicenses(principal.tenantId(), pageable));
    }

    @GetMapping("/expiring")
    @Operation(summary = "Get licenses expiring soon", description = "SYSTEM_ADMIN only. Returns licenses expiring within the given days (default 7).")
    public ResponseEntity<List<LicenseResponse>> getExpiringLicenses(
            @RequestParam(defaultValue = "7") @Parameter(description = "Number of days ahead to check") int days) {
        return ResponseEntity.ok(licenseService.getExpiringLicenses(days));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get license by ID", description = "Authenticated users can view license details.")
    public ResponseEntity<LicenseResponse> getLicense(@PathVariable UUID id) {
        return ResponseEntity.ok(licenseService.getLicense(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update license", description = "SYSTEM_ADMIN only. Supports partial updates.")
    public ResponseEntity<LicenseResponse> updateLicense(@PathVariable UUID id,
                                                         @Valid @RequestBody UpdateLicenseRequest request,
                                                         @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(licenseService.updateLicense(id, request, principal.username()));
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Revoke a license", description = "SYSTEM_ADMIN only. Sets license status to REVOKED (soft delete).")
    public ResponseEntity<LicenseResponse> revokeLicense(@PathVariable UUID id,
                                                         @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(licenseService.revokeLicense(id, principal.username()));
    }

    // ── Internal API for Collector service ──

    @GetMapping("/internal/policies")
    @Operation(summary = "Get all active policies", description = "Internal API for Collector service. No auth required.")
    public ResponseEntity<List<PolicyDTO>> getActivePolicies() {
        return ResponseEntity.ok(licenseService.getAllActivePolicies());
    }
}