package com.vdt.soc.aggregate.controller;

import com.vdt.soc.aggregate.dto.MetricsResponse;
import com.vdt.soc.aggregate.dto.TimeseriesResponse;
import com.vdt.soc.aggregate.service.MetricsService;
import com.vdt.soc.common.core.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "EPS metrics & time-series APIs")
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping("/tenants/{tenantId}/metrics/current")
    @Operation(summary = "Get current EPS and monthly usage snapshot")
    public ResponseEntity<?> getCurrentMetrics(@PathVariable UUID tenantId) {
        MetricsResponse response = metricsService.getCurrentMetrics(tenantId);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .timestamp(Instant.now())
                            .status(404)
                            .error("Not Found")
                            .message("No active policy for tenant " + tenantId)
                            .path("/api/v1/tenants/" + tenantId + "/metrics/current")
                            .build());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tenants/{tenantId}/metrics/timeseries")
    @Operation(summary = "Get time-series data in Grafana-compatible format",
            description = "Returns [{\"target\":\"...\",\"datapoints\":[[value,ts_ms],...]}]")
    public ResponseEntity<List<TimeseriesResponse>> getTimeseries(
            @PathVariable UUID tenantId,
            @RequestParam @Parameter(description = "Start time ISO 8601", example = "2026-06-28T06:00:00Z") Instant from,
            @RequestParam @Parameter(description = "End time ISO 8601", example = "2026-06-28T12:00:00Z") Instant to,
            @RequestParam(defaultValue = "1m") @Parameter(description = "Bucket size: 1m, 5m, 15m, 1h") String interval,
            @RequestParam(defaultValue = "accepted_eps,dropped_eps")
            @Parameter(description = "Comma-separated metric names") String targets) {

        List<String> targetList = Arrays.asList(targets.split(","));
        return ResponseEntity.ok(metricsService.buildTimeseries(tenantId, from, to, interval, targetList));
    }
}