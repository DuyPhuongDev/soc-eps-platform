package com.vdt.soc.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.internal-api")
public class InternalApiProperties {

    /**
     * Base URL of the tenant-service (e.g. <a href="http://localhost:8082">...</a>).
     */
    private String tenantServiceUrl = "http://localhost:8082";

    /**
     * Base URL of the license-service (e.g. <a href="http://localhost:8083">...</a>).
     */
    private String licenseServiceUrl = "http://localhost:8083";

    /**
     * Shared secret for authenticating to tenant-service's internal API.
     * Sent as X-Internal-Secret header.
     */
    private String internalSecret = "change-me-please";

    /**
     * Connection timeout in milliseconds for WebClient calls.
     */
    private int connectTimeoutMs = 3000;

    /**
     * Read timeout in milliseconds for WebClient calls.
     */
    private int readTimeoutMs = 5000;
}
