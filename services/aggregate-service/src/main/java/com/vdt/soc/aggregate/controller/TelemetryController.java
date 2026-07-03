package com.vdt.soc.aggregate.controller;

import com.vdt.soc.aggregate.dto.DroppedEventPoint;
import com.vdt.soc.aggregate.dto.DroppedTodayResponse;
import com.vdt.soc.aggregate.dto.EpsCurrentResponse;
import com.vdt.soc.aggregate.dto.EpsHistoryPoint;
import com.vdt.soc.aggregate.dto.LicenseStatusResponse;
import com.vdt.soc.aggregate.dto.QuotaMonthlyResponse;
import com.vdt.soc.aggregate.dto.UsageSummaryRow;
import com.vdt.soc.aggregate.dto.UsageTodayResponse;
import com.vdt.soc.aggregate.service.TelemetryService;
import com.vdt.soc.common.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
@Tag(name = "Telemetry", description = "Dashboard telemetry: metric cards, charts, and usage table")
public class TelemetryController {

    private final TelemetryService telemetryService;

    // ── 2.1 Current EPS ────────────────────────────────────────────────

    @GetMapping("/eps-current")
    @Operation(summary = "Get current EPS snapshot",
            description = "Returns real-time accepted/dropped EPS with 15-point sparklines")
    public ResponseEntity<EpsCurrentResponse> getEpsCurrent(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(telemetryService.getEpsCurrent(principal.tenantId()));
    }

    // ── 2.2 Today's Usage ──────────────────────────────────────────────

    @GetMapping("/usage-today")
    @Operation(summary = "Get today's usage",
            description = "Returns accumulated accepted events today vs daily quota")
    public ResponseEntity<UsageTodayResponse> getUsageToday(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(telemetryService.getUsageToday(principal.tenantId()));
    }

    // ── 2.3 Monthly Quota ──────────────────────────────────────────────

    @GetMapping("/quota-monthly")
    @Operation(summary = "Get monthly quota usage",
            description = "Returns used, total, remaining, percentage, and reset info")
    public ResponseEntity<QuotaMonthlyResponse> getQuotaMonthly(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(telemetryService.getQuotaMonthly(principal.tenantId()));
    }

    // ── 2.4 Dropped Events Today ───────────────────────────────────────

    @GetMapping("/dropped-today")
    @Operation(summary = "Get dropped events today",
            description = "Returns total dropped today with 12-point sparkline and drop rate")
    public ResponseEntity<DroppedTodayResponse> getDroppedToday(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(telemetryService.getDroppedToday(principal.tenantId()));
    }

    // ── 2.5 License Status ─────────────────────────────────────────────

    @GetMapping("/license")
    @Operation(summary = "Get license status",
            description = "Returns days left until license expiry and expiry date")
    public ResponseEntity<LicenseStatusResponse> getLicenseStatus(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(telemetryService.getLicenseStatus(principal.tenantId()));
    }

    // ── 3.1 EPS History ────────────────────────────────────────────────

    @GetMapping("/eps-history")
    @Operation(summary = "Get EPS history for chart",
            description = "Returns accepted vs dropped EPS time-series for line/area chart")
    public ResponseEntity<List<EpsHistoryPoint>> getEpsHistory(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "1000") int maxDataPoints) {

        return ResponseEntity.ok(
                telemetryService.getEpsHistory(
                        principal.tenantId(),
                        Instant.ofEpochMilli(from),
                        Instant.ofEpochMilli(to),
                        maxDataPoints
                )
        );
    }

    // ── 3.2 Dropped Events ─────────────────────────────────────────────

    @GetMapping("/dropped-events-last-24h")
    @Operation(summary = "Get dropped events history for bar chart",
            description = "Returns hourly dropped event counts")
    public ResponseEntity<List<DroppedEventPoint>> getDroppedEventsLast24h(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(telemetryService.getDroppedEventsLast24h(principal.tenantId()));
    }

    // ── 4.1 Usage Summary ──────────────────────────────────────────────

    @GetMapping("/usage-summary")
    @Operation(summary = "Get usage summary table",
            description = "Returns 5 rows (Last 5m, 15m, 1h, 6h, 24h) with accepted, dropped, avgEps, maxEps, dropRate")
    public ResponseEntity<List<UsageSummaryRow>> getUsageSummary(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(telemetryService.getUsageSummary(principal.tenantId()));
    }
}
