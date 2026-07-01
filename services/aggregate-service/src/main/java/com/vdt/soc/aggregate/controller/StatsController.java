package com.vdt.soc.aggregate.controller;

import com.vdt.soc.aggregate.dto.DropStatsResponse;
import com.vdt.soc.aggregate.service.DropStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Stats", description = "Drop event statistics APIs")
public class StatsController {

    private final DropStatsService dropStatsService;

    @GetMapping("/tenants/{tenantId}/stats/dropped")
    @Operation(summary = "Get dropped event statistics")
    public ResponseEntity<DropStatsResponse> getDropStats(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "day")
            @Parameter(description = "Period: day, week, or month") String period) {
        return ResponseEntity.ok(dropStatsService.getDropStats(tenantId, period));
    }
}