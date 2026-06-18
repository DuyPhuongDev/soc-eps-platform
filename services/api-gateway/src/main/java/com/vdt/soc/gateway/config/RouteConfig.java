package com.vdt.soc.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // ── Tenant Service ──
                .route("tenant-service", r -> r
                        .path("/api/v1/tenants/**", "/api/v1/auth/**")
                        .uri("lb://tenant-service"))

                // ── License Service ──
                .route("license-service", r -> r
                        .path("/api/v1/licenses/**")
                        .uri("lb://license-service"))

                // ── Collector Service ──
                .route("collector-service", r -> r
                        .path("/api/v1/events/**")
                        .uri("lb://collector-service"))

                // ── Dashboard Service (future) ──
                .route("dashboard-service", r -> r
                        .path("/api/v1/dashboard/**")
                        .uri("lb://dashboard-service"))

                // ── Metric Aggregator (future) ──
                .route("metric-aggregator", r -> r
                        .path("/api/v1/metrics/**")
                        .uri("lb://metric-aggregator"))

                .build();
    }
}
