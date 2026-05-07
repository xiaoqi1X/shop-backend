package com.hmall.trade.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.trade.config.SeckillAsyncProperties;
import com.hmall.trade.domain.mq.SeckillOrderMessage;
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
        producer.start();
    }

    public void send(SeckillOrderMessage orderMessage) {
        try {
            SeckillAsyncProperties.Rocketmq rocketmq = properties.getRocketmq();
            byte[] body = objectMapper.writeValueAsString(orderMessage).getBytes(StandardCharsets.UTF_8);
            Message message = new Message(rocketmq.getOrderTopic(), rocketmq.getOrderTag(), orderMessage.getRequestId(), body);
            SendResult result = producer.send(message);
            if (result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("Unexpected RocketMQ send status: " + result.getSendStatus());
            }
            log.debug("Sent seckill order message, requestId={}, orderId={}, msgId={}",
                    orderMessage.getRequestId(), orderMessage.getOrderId(), result.getMsgId());
        } catch (Exception e) {
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
