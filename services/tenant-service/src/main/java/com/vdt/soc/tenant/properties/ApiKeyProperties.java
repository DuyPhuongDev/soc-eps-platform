package com.vdt.soc.tenant.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.api-key")
public class ApiKeyProperties {

    private String prefix = "eps_";
    private int randomBytes = 32;
}
