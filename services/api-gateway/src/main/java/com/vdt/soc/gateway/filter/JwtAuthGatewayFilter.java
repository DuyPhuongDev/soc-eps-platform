package com.vdt.soc.gateway.filter;

import com.vdt.soc.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reactive JWT authentication filter for Spring Cloud Gateway.
 * Parses and validates JWT tokens using the shared {@link JwtUtil}
 * from common-security, adapted for the reactive WebFlux environment.
 * <p>
 * Skipped paths: /api/v1/auth/login, /api/v1/events (API key auth).
 */
@Component
public class JwtAuthGatewayFilter extends AbstractGatewayFilterFactory<Object> {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/events"
    );
    private final JwtUtil jwtUtil;

    public JwtAuthGatewayFilter(JwtUtil jwtUtil) {
        super(Object.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public String name() {
        return "JwtAuth";
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            // Skip auth for public paths
            if (isPublicPath(path)) {
                return chain.filter(exchange);
            }

            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String token = authHeader.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token).getPayload();
                // Token is valid — claims are verified by JwtUtil
            } catch (ExpiredJwtException | SignatureException | MalformedJwtException | IllegalArgumentException e) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return chain.filter(exchange);
        };
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
