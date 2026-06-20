package com.vdt.soc.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.vdt.soc.collector", "com.vdt.soc.common.kafka", "com.vdt.soc.common.redis", "com.vdt.soc.common.etcd", "com.vdt.soc.common.core"})
@ConfigurationPropertiesScan
public class CollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollectorApplication.class, args);
    }
}
