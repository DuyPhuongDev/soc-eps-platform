package com.vdt.soc.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.internal-api")
public class InternalApiProperties {

    private String tenantServiceUrl = "http://localhost:8082";

    private String licenseServiceUrl = "http://localhost:8083";

    private String internalSecret = "change-me-please";

    private int connectTimeoutMs = 3000;

    private String apiKeyUri = "/api/v1/internal/api-keys";

    private String policyUri = "/api/v1/licenses/internal/policies";

    private int readTimeoutMs = 5000;
}
