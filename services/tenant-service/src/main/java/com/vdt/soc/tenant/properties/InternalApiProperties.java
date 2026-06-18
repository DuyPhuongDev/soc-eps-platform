package com.vdt.soc.tenant.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.internal")
public class InternalApiProperties {

    private String secret;
    private String headerName = "X-Internal-Secret";
}
