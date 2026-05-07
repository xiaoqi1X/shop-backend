package com.hmall.trade.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.trade.config.SeckillAsyncProperties;
import com.hmall.trade.domain.mq.SeckillOrderMessage;
import com.hmall.trade.service.SeckillOrderFinalizeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderMessageConsumer implements InitializingBean, DisposableBean {

    private final SeckillAsyncProperties properties;
    private final ObjectMapper objectMapper;
    private final SeckillOrderFinalizeService finalizeService;

    private DefaultMQPushConsumer consumer;

    @Override
    public void afterPropertiesSet() throws Exception {
        SeckillAsyncProperties.Rocketmq rocketmq = properties.getRocketmq();
        consumer = new DefaultMQPushConsumer(rocketmq.getOrderConsumerGroup());
        consumer.setNamesrvAddr(rocketmq.getNameServer());
        consumer.subscribe(rocketmq.getOrderTopic(), rocketmq.getOrderTag());
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    SeckillOrderMessage orderMessage = objectMapper.readValue(
                            new String(msg.getBody(), StandardCharsets.UTF_8),
                            SeckillOrderMessage.class
                    );
                    finalizeService.finalizeOrder(orderMessage);
                    log.debug("Finalized seckill order, requestId={}, orderId={}, msgId={}",
                            orderMessage.getRequestId(), orderMessage.getOrderId(), msg.getMsgId());
                } catch (Exception e) {
                    log.error("Failed to consume seckill order message, msgId={}", msg.getMsgId(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
    }

    @Override
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
