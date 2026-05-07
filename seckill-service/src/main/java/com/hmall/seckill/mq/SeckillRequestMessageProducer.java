package com.hmall.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
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
public class SeckillRequestMessageProducer implements InitializingBean, DisposableBean {

    private final SeckillAsyncProperties properties;
    private final ObjectMapper objectMapper;

    private DefaultMQProducer producer;

    @Override
    public void afterPropertiesSet() throws Exception {
        SeckillAsyncProperties.Rocketmq rocketmq = properties.getRocketmq();
        producer = new DefaultMQProducer(rocketmq.getRequestProducerGroup());
        producer.setNamesrvAddr(rocketmq.getNameServer());
        producer.start();
    }

    public void send(SeckillRequestMessage requestMessage) {
        try {
            SeckillAsyncProperties.Rocketmq rocketmq = properties.getRocketmq();
            byte[] body = objectMapper.writeValueAsString(requestMessage).getBytes(StandardCharsets.UTF_8);
            Message message = new Message(rocketmq.getRequestTopic(), rocketmq.getRequestTag(), requestMessage.getRequestId(), body);
            SendResult result = producer.send(message);
            if (result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("Unexpected RocketMQ send status: " + result.getSendStatus());
            }
            log.debug("Sent seckill request message, requestId={}, msgId={}", requestMessage.getRequestId(), result.getMsgId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to send seckill request message, requestId=" + requestMessage.getRequestId(), e);
        }
    }

    @Override
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
        }
    }
}
