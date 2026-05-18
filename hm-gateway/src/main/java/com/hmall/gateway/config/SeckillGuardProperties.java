package com.hmall.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "hm.seckill.guard")
public class SeckillGuardProperties {

    private boolean enabled = true;

    private String orderPath = "/seckill/orders";

    private int maxBodyBytes = 2048;
}
