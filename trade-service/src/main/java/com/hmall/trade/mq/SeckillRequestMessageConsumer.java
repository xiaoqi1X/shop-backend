package com.hmall.trade.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.trade.config.SeckillAsyncProperties;
import com.hmall.trade.domain.enums.SeckillQuotaResult;
import com.hmall.trade.domain.mq.SeckillOrderMessage;
import com.hmall.trade.domain.mq.SeckillRequestMessage;
import com.hmall.trade.service.SeckillQuotaService;
import com.hmall.trade.service.SeckillResultPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
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
public class SeckillRequestMessageConsumer implements InitializingBean, DisposableBean {

    private final SeckillAsyncProperties properties;
    private final ObjectMapper objectMapper;
    private final SeckillQuotaService quotaService;
    private final SeckillOrderMessageProducer orderMessageProducer;
    private final IdentifierGenerator identifierGenerator;
    private final SeckillResultPushService resultPushService;

    private DefaultMQPushConsumer consumer;
    private LocalTokenBucket tokenBucket;

    @Override
    public void afterPropertiesSet() throws Exception {
        SeckillAsyncProperties.Rocketmq rocketmq = properties.getRocketmq();
        SeckillAsyncProperties.TokenBucket tokenBucketProperties = properties.getTokenBucket();
        tokenBucket = new LocalTokenBucket(tokenBucketProperties.getPermitsPerSecond(), tokenBucketProperties.getBurstCapacity());

        consumer = new DefaultMQPushConsumer(rocketmq.getRequestConsumerGroup());
        consumer.setNamesrvAddr(rocketmq.getNameServer());
        consumer.subscribe(rocketmq.getRequestTopic(), rocketmq.getRequestTag());
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    tokenBucket.acquire();
                    SeckillRequestMessage requestMessage = objectMapper.readValue(
                            new String(msg.getBody(), StandardCharsets.UTF_8),
                            SeckillRequestMessage.class
                    );
                    SeckillQuotaResult result = quotaService.tryAcquire(requestMessage);
                    if (result == SeckillQuotaResult.SUCCESS) {
                        try {
                            orderMessageProducer.send(buildOrderMessage(requestMessage));
                            resultPushService.push(requestMessage, com.hmall.trade.domain.enums.SeckillStatus.SUCCESS, null);
                        } catch (RuntimeException e) {
                            quotaService.release(requestMessage);
                            throw e;
                        }
                    } else if (result == SeckillQuotaResult.DUPLICATE) {
                        resultPushService.push(requestMessage, com.hmall.trade.domain.enums.SeckillStatus.DUPLICATE_ORDER, null);
                    } else if (result == SeckillQuotaResult.SOLD_OUT) {
                        resultPushService.push(requestMessage, com.hmall.trade.domain.enums.SeckillStatus.SOLD_OUT, null);
                    } else {
                        resultPushService.push(requestMessage, com.hmall.trade.domain.enums.SeckillStatus.FAILED, null);
                    }
                    log.debug("Consumed seckill request, requestId={}, result={}, msgId={}",
                            requestMessage.getRequestId(), result, msg.getMsgId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                } catch (Exception e) {
                    log.error("Failed to consume seckill request message, msgId={}", msg.getMsgId(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
    }

    private SeckillOrderMessage buildOrderMessage(SeckillRequestMessage requestMessage) {
        return new SeckillOrderMessage()
                .setOrderId(identifierGenerator.nextId(requestMessage).longValue())
                .setRequestId(requestMessage.getRequestId())
                .setSeckillId(requestMessage.getSeckillId())
                .setItemId(requestMessage.getItemId())
                .setUserId(requestMessage.getUserId())
                .setNum(requestMessage.getNum())
                .setSeckillPrice(requestMessage.getSeckillPrice())
                .setTotalFee(requestMessage.getTotalFee())
                .setCreateTime(requestMessage.getCreateTime());
    }

    @Override
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
