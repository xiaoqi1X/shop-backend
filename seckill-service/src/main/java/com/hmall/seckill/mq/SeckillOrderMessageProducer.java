package com.hmall.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.mq.SeckillOrderMessage;
import com.hmall.seckill.support.SeckillMetricsLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderMessageProducer implements InitializingBean, DisposableBean {

    private final SeckillAsyncProperties properties;
    private final ObjectMapper objectMapper;

    private DefaultMQProducer producer;

    @Override
    public void afterPropertiesSet() throws Exception {
        SeckillAsyncProperties.Rocketmq rocketmq = properties.getRocketmq();
        producer = new DefaultMQProducer(rocketmq.getOrderProducerGroup());
        producer.setNamesrvAddr(rocketmq.getNameServer());
        applyProducerProperties(rocketmq);
        log.info("Starting seckill order producer, sendTimeoutMs={}, retryTimesWhenSendFailed={}, retryTimesWhenSendAsyncFailed={}",
                rocketmq.getProducer().getSendTimeoutMs(),
                rocketmq.getProducer().getRetryTimesWhenSendFailed(),
                rocketmq.getProducer().getRetryTimesWhenSendAsyncFailed());
        producer.start();
    }

    private void applyProducerProperties(SeckillAsyncProperties.Rocketmq rocketmq) {
        SeckillAsyncProperties.Producer producerProperties = rocketmq.getProducer();
        if (producerProperties.getSendTimeoutMs() > 0) {
            producer.setSendMsgTimeout(producerProperties.getSendTimeoutMs());
        }
        if (producerProperties.getRetryTimesWhenSendFailed() >= 0) {
            producer.setRetryTimesWhenSendFailed(producerProperties.getRetryTimesWhenSendFailed());
        }
        if (producerProperties.getRetryTimesWhenSendAsyncFailed() >= 0) {
            producer.setRetryTimesWhenSendAsyncFailed(producerProperties.getRetryTimesWhenSendAsyncFailed());
        }
    }

    public void send(SeckillOrderMessage orderMessage) {
        long startedAt = SeckillMetricsLogger.start();
        try {
            SeckillAsyncProperties.Rocketmq rocketmq = properties.getRocketmq();
            byte[] body = objectMapper.writeValueAsString(orderMessage).getBytes(StandardCharsets.UTF_8);
            Message message = new Message(rocketmq.getOrderTopic(), rocketmq.getOrderTag(), orderMessage.getRequestId(), body);
            SendResult result = producer.send(message);
            if (result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("Unexpected RocketMQ send status: " + result.getSendStatus());
            }
            SeckillMetricsLogger.info("order_mq_send", "requestId", orderMessage.getRequestId(), "orderId", orderMessage.getOrderId(), "seckillId", orderMessage.getSeckillId(), "userId", orderMessage.getUserId(), "msgId", result.getMsgId(), "sendStatus", result.getSendStatus(), "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            log.debug("Sent seckill order message, requestId={}, orderId={}, msgId={}",
                    orderMessage.getRequestId(), orderMessage.getOrderId(), result.getMsgId());
        } catch (Exception e) {
            SeckillMetricsLogger.warn("order_mq_send", e, "requestId", orderMessage == null ? null : orderMessage.getRequestId(), "orderId", orderMessage == null ? null : orderMessage.getOrderId(), "seckillId", orderMessage == null ? null : orderMessage.getSeckillId(), "userId", orderMessage == null ? null : orderMessage.getUserId(), "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            throw new RuntimeException("Failed to send seckill order message, requestId=" + orderMessage.getRequestId(), e);
        }
    }

    @Override
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
        }
    }
}
