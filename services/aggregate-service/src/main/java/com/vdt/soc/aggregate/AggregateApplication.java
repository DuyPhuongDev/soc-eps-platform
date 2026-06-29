package com.vdt.soc.aggregate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication(scanBasePackages = {
        "com.vdt.soc.aggregate",
        "com.vdt.soc.common.security",
        "com.vdt.soc.common.core",
        "com.vdt.soc.common.etcd"})
@EnableDiscoveryClient
@EnableScheduling
@EnableMethodSecurity
public class AggregateApplication {

    public static void main(String[] args) {
        SpringApplication.run(AggregateApplication.class, args);
    }
}