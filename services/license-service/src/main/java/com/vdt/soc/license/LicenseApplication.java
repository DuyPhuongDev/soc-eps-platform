package com.vdt.soc.license;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication(scanBasePackages = {"com.vdt.soc.license", "com.vdt.soc.common.security", "com.vdt.soc.common.core", "com.vdt.soc.common.etcd"})
@EnableDiscoveryClient
@EnableMethodSecurity
@EnableScheduling
public class LicenseApplication {

    public static void main(String[] args) {
        SpringApplication.run(LicenseApplication.class, args);
    }

}