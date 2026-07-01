package com.vdt.soc.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    /**
     * Interval in seconds between cache refresh cycles.
     */
    private int refreshIntervalSeconds = 30;

    /**
     * Directory where snapshot files are stored.
     */
    private String snapshotDir = "config/snapshots";

    /**
     * Snapshot file name for policy cache.
     */
    private String policySnapshotFile = "policies.json";

    /**
     * Snapshot file name for API key cache.
     */
    private String apiKeySnapshotFile = "api-keys.json";
}
