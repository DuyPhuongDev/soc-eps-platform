package com.vdt.soc.common.etcd;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for etcd connection.
 */
@Data
@ConfigurationProperties(prefix = "app.etcd")
public class EtcdProperties {

    /**
     * Comma-separated list of etcd endpoints.
     * e.g. "http://localhost:2379"
     */
    private String endpoints = "http://localhost:2379";

    /**
     * Prefix for all policy keys in etcd.
     */
    private String keyPrefix = "/policies/";

    /**
     * Connection timeout in milliseconds.
     */
    private int connectTimeoutMs = 5000;

    /**
     * Watch retry delay in milliseconds.
     */
    private long watchRetryDelayMs = 3000;
}