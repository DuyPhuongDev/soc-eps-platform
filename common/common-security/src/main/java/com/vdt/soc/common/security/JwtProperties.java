package com.vdt.soc.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    private long expirationSeconds = 3600;
    private String issuer = "eps-tenant-service";
}