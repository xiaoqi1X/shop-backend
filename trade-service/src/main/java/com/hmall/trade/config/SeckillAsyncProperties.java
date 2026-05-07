package com.hmall.trade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "seckill.async")
public class SeckillAsyncProperties {

    private Rocketmq rocketmq = new Rocketmq();
    private Redis redis = new Redis();
    private TokenBucket tokenBucket = new TokenBucket();
    private Websocket websocket = new Websocket();

    @Data
    public static class Rocketmq {
        private String nameServer;
        private String requestTopic;
        private String requestTag;
        private String requestProducerGroup;
        private String requestConsumerGroup;
        private String orderTopic;
        private String orderTag;
        private String orderProducerGroup;
        private String orderConsumerGroup;
    }

    @Data
    public static class Redis {
        private String stockKeyPrefix;
        private String usersKeyPrefix;
    }

    @Data
    public static class TokenBucket {
        private int permitsPerSecond;
        private int burstCapacity;
    }

    @Data
    public static class Websocket {
        private String endpoint;
    }
}
