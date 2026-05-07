package com.hmall.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.exception.SeckillStockDeductFailedException;
import com.hmall.seckill.domain.mq.SeckillOrderMessage;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.service.SeckillOrderFinalizeService;
import com.hmall.seckill.service.SeckillQuotaService;
import com.hmall.seckill.service.SeckillResultPushService;
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
    private final SeckillQuotaService quotaService;
    private final SeckillResultPushService resultPushService;

    private DefaultMQPushConsumer consumer;

    @Override
    public void afterPropertiesSet() throws Exception {
        SeckillAsyncProperties.Rocketmq rocketmq = properties.getRocketmq();
        consumer = new DefaultMQPushConsumer(rocketmq.getOrderConsumerGroup());
        consumer.setNamesrvAddr(rocketmq.getNameServer());
        consumer.subscribe(rocketmq.getOrderTopic(), rocketmq.getOrderTag());
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                SeckillOrderMessage orderMessage = null;
                try {
                    orderMessage = objectMapper.readValue(
                            new String(msg.getBody(), StandardCharsets.UTF_8),
                            SeckillOrderMessage.class
                    );
                    boolean finalized = finalizeService.finalizeOrder(orderMessage);
                    if (finalized) {
                        resultPushService.push(toRequestMessage(orderMessage), SeckillStatus.SUCCESS, null);
                    }
                    log.debug("Finalized seckill order, requestId={}, orderId={}, msgId={}",
                            orderMessage.getRequestId(), orderMessage.getOrderId(), msg.getMsgId());
                } catch (SeckillStockDeductFailedException e) {
                    if (orderMessage == null) {
                        log.error("Failed to handle stock deduction failure because order message was not parsed, msgId={}", msg.getMsgId(), e);
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                    SeckillRequestMessage requestMessage = toRequestMessage(orderMessage);
                    quotaService.release(requestMessage);
                    resultPushService.push(requestMessage, SeckillStatus.SOLD_OUT, "Sold out");
                    log.warn("Database seckill stock exhausted, requestId={}, msgId={}",
                            orderMessage.getRequestId(), msg.getMsgId());
                } catch (Exception e) {
                    log.error("Failed to consume seckill order message, msgId={}", msg.getMsgId(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
    }

    private SeckillRequestMessage toRequestMessage(SeckillOrderMessage orderMessage) {
        return new SeckillRequestMessage()
                .setRequestId(orderMessage.getRequestId())
                .setSeckillId(orderMessage.getSeckillId())
                .setItemId(orderMessage.getItemId())
                .setUserId(orderMessage.getUserId())
                .setNum(orderMessage.getNum())
                .setSeckillPrice(orderMessage.getSeckillPrice())
                .setTotalFee(orderMessage.getTotalFee())
                .setCreateTime(orderMessage.getCreateTime());
    }

    @Override
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
