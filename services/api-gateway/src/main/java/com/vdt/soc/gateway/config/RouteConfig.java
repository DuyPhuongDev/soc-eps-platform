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

                // ── notification Service ──
                .route("notification-service", r -> r
                        .path("/api/v1/alerts/**")
                        .uri("lb://notification-service"))

                // ── Telemetry / Aggregate Service ──
                .route("aggregate-service", r -> r
                        .path("/api/v1/telemetry/**", "/api/v1/tenants/*/metrics/**",
                                "/api/v1/tenants/*/stats/**", "/api/v1/alerts/**")
                        .uri("lb://aggregate-service"))

                .build();
    }
}
