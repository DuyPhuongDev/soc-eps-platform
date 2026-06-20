package com.vdt.soc.license.config;

import com.vdt.soc.common.security.JwtAuthenticationFilter;
import com.vdt.soc.common.security.JwtProperties;
import com.vdt.soc.common.security.JwtUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    @Bean
    public JwtUtil jwtUtil(JwtProperties properties) {
        return new JwtUtil(properties);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil) {
        return new JwtAuthenticationFilter(jwtUtil);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Actuator & Swagger
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()
                        // Internal API (Collector service)
                        .requestMatchers("/api/v1/internal/**", "/api/v1/licenses/internal/**").permitAll()
                        // SYSTEM_ADMIN only: create, update, revoke, expiring, audit-logs
                        .requestMatchers(HttpMethod.POST, "/api/v1/licenses").hasRole(SYSTEM_ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/licenses/**").hasRole(SYSTEM_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/licenses/*/revoke").hasRole(SYSTEM_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/licenses/expiring").hasRole(SYSTEM_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/licenses/*/audit-logs").hasRole(SYSTEM_ADMIN)
//                         Authenticated: read licenses
                        .requestMatchers(HttpMethod.GET, "/api/v1/licenses/**").authenticated()
                        // Everything else
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\": \"Access Denied: You do not have permission!\"}");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}