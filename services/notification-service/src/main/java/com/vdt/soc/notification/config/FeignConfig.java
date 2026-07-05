package com.vdt.soc.notification.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign configuration: adds the {@code X-Internal-Secret} header to every
 * request so that tenant-service internal endpoints allow the call.
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor internalSecretInterceptor(
            @Value("${app.internal.secret:}") String secret) {
        return template -> {
            if (secret != null && !secret.isBlank()) {
                template.header("X-Internal-Secret", secret);
            }
        };
    }
}
