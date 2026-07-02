package com.vdt.soc.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {


    private int refreshIntervalSeconds = 30;

}
