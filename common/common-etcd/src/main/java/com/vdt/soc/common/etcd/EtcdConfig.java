package com.vdt.soc.common.etcd;

import io.etcd.jetcd.Client;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * etcd client configuration.
 * Provides a jetcd Client bean for services that need to publish or watch policies.
 */
@Configuration
@EnableConfigurationProperties(EtcdProperties.class)
public class EtcdConfig {

    @Bean(destroyMethod = "close")
    public Client etcdClient(EtcdProperties properties) {
        return Client.builder()
                .endpoints(properties.getEndpoints().split(","))
                .connectTimeout(java.time.Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }
}