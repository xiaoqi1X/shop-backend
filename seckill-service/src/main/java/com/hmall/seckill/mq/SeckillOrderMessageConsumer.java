package com.hmall.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.exception.SeckillStockDeductFailedException;
import com.hmall.seckill.domain.mq.SeckillOrderMessage;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.service.SeckillOrderFinalizeService;
import com.hmall.seckill.service.SeckillOrderOutboxService;
import com.hmall.seckill.service.SeckillQuotaService;
import com.hmall.seckill.service.SeckillResultPushService;
import com.hmall.seckill.support.SeckillMetricsLogger;
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
    private final SeckillOrderOutboxService outboxService;

    private DefaultMQPushConsumer consumer;
    private LocalTokenBucket tokenBucket;

    @Override
    public void afterPropertiesSet() throws Exception {
        SeckillAsyncProperties.Rocketmq rocketmq = properties.getRocketmq();
        SeckillAsyncProperties.TokenBucket tokenBucketProperties = properties.getTokenBucket();
        tokenBucket = new LocalTokenBucket(tokenBucketProperties.getPermitsPerSecond(), tokenBucketProperties.getBurstCapacity());
        log.info("Starting seckill order finalization token bucket, permitsPerSecond={}, burstCapacity={}",
                tokenBucketProperties.getPermitsPerSecond(), tokenBucketProperties.getBurstCapacity());
        consumer = new DefaultMQPushConsumer(rocketmq.getOrderConsumerGroup());
        consumer.setNamesrvAddr(rocketmq.getNameServer());
        applyConsumerProperties(rocketmq.getOrderConsumer());
        log.info("Starting seckill order consumer rocketmq settings, consumeThreadMin={}, consumeThreadMax={}, consumeMessageBatchMaxSize={}, consumeTimeoutMinutes={}, maxReconsumeTimes={}",
                rocketmq.getOrderConsumer().getConsumeThreadMin(),
                rocketmq.getOrderConsumer().getConsumeThreadMax(),
                rocketmq.getOrderConsumer().getConsumeMessageBatchMaxSize(),
                rocketmq.getOrderConsumer().getConsumeTimeoutMinutes(),
                rocketmq.getOrderConsumer().getMaxReconsumeTimes());
        consumer.subscribe(rocketmq.getOrderTopic(), rocketmq.getOrderTag());
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                if (handleMessage(msg) == ConsumeConcurrentlyStatus.RECONSUME_LATER) {
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
    }

    ConsumeConcurrentlyStatus handleMessage(MessageExt msg) {
        long startedAt = SeckillMetricsLogger.start();
        SeckillOrderMessage orderMessage = null;
        try {
            orderMessage = objectMapper.readValue(
                    new String(msg.getBody(), StandardCharsets.UTF_8),
                    SeckillOrderMessage.class
            );
            long tokenStartedAt = SeckillMetricsLogger.start();
            if (tokenBucket != null) {
                tokenBucket.acquire();
            }
            long tokenWaitMs = SeckillMetricsLogger.elapsedMs(tokenStartedAt);
            long finalizeStartedAt = SeckillMetricsLogger.start();
            boolean finalized = finalizeService.finalizeOrder(orderMessage);
            long finalizeMs = SeckillMetricsLogger.elapsedMs(finalizeStartedAt);
            SeckillRequestMessage requestMessage = toRequestMessage(orderMessage);
            if (finalized) {
                resultPushService.push(requestMessage, SeckillStatus.SUCCESS, null, orderMessage.getOrderId());
            } else {
                resultPushService.push(requestMessage, SeckillStatus.SUCCESS, "Order already finalized", orderMessage.getOrderId());
            }
            outboxService.markFinalized(orderMessage.getRequestId());
            SeckillMetricsLogger.info("order_consume", "requestId", orderMessage.getRequestId(), "orderId", orderMessage.getOrderId(), "seckillId", orderMessage.getSeckillId(), "userId", orderMessage.getUserId(), "msgId", msg.getMsgId(), "finalized", finalized, "tokenWaitMs", tokenWaitMs, "finalizeMs", finalizeMs, "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            log.debug("Finalized seckill order, requestId={}, orderId={}, msgId={}",
                    orderMessage.getRequestId(), orderMessage.getOrderId(), msg.getMsgId());
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (SeckillStockDeductFailedException e) {
            if (orderMessage == null) {
                SeckillMetricsLogger.warn("order_consume", e, "msgId", msg.getMsgId(), "result", "STOCK_FAILED_UNPARSED", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
                log.error("Failed to handle stock deduction failure because order message was not parsed, msgId={}", msg.getMsgId(), e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            SeckillRequestMessage requestMessage = toRequestMessage(orderMessage);
            quotaService.release(requestMessage);
            resultPushService.push(requestMessage, SeckillStatus.SOLD_OUT, "Sold out");
            outboxService.markFailed(orderMessage.getRequestId(), "Database seckill stock exhausted");
            SeckillMetricsLogger.warn("order_consume", e, "requestId", orderMessage.getRequestId(), "orderId", orderMessage.getOrderId(), "seckillId", orderMessage.getSeckillId(), "userId", orderMessage.getUserId(), "msgId", msg.getMsgId(), "result", "STOCK_DEDUCT_FAILED", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            log.warn("Database seckill stock exhausted, requestId={}, msgId={}",
                    orderMessage.getRequestId(), msg.getMsgId());
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SeckillMetricsLogger.warn("order_consume", e, "requestId", orderMessage == null ? null : orderMessage.getRequestId(), "orderId", orderMessage == null ? null : orderMessage.getOrderId(), "seckillId", orderMessage == null ? null : orderMessage.getSeckillId(), "userId", orderMessage == null ? null : orderMessage.getUserId(), "msgId", msg.getMsgId(), "result", "INTERRUPTED", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        } catch (Exception e) {
            if (orderMessage != null && isFinalRetry(msg, properties.getRocketmq().getOrderConsumer())) {
                resultPushService.push(toRequestMessage(orderMessage), SeckillStatus.FAILED, "Seckill order finalization failed after retries");
                outboxService.markFailed(orderMessage.getRequestId(), "Seckill order finalization failed after retries");
            }
            SeckillMetricsLogger.warn("order_consume", e, "requestId", orderMessage == null ? null : orderMessage.getRequestId(), "orderId", orderMessage == null ? null : orderMessage.getOrderId(), "seckillId", orderMessage == null ? null : orderMessage.getSeckillId(), "userId", orderMessage == null ? null : orderMessage.getUserId(), "msgId", msg.getMsgId(), "result", "EXCEPTION", "finalRetry", isFinalRetry(msg, properties.getRocketmq().getOrderConsumer()), "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            log.error("Failed to consume seckill order message, msgId={}", msg.getMsgId(), e);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }

    private void applyConsumerProperties(SeckillAsyncProperties.Consumer consumerProperties) {
        if (consumerProperties.getConsumeThreadMin() > 0) {
            consumer.setConsumeThreadMin(consumerProperties.getConsumeThreadMin());
        }
        if (consumerProperties.getConsumeThreadMax() > 0) {
            consumer.setConsumeThreadMax(consumerProperties.getConsumeThreadMax());
        }
        if (consumerProperties.getConsumeMessageBatchMaxSize() > 0) {
            consumer.setConsumeMessageBatchMaxSize(consumerProperties.getConsumeMessageBatchMaxSize());
        }
        if (consumerProperties.getConsumeTimeoutMinutes() > 0) {
            consumer.setConsumeTimeout(consumerProperties.getConsumeTimeoutMinutes());
        }
        if (consumerProperties.getMaxReconsumeTimes() >= 0) {
            consumer.setMaxReconsumeTimes(consumerProperties.getMaxReconsumeTimes());
        }
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

    private boolean isFinalRetry(MessageExt msg, SeckillAsyncProperties.Consumer consumerProperties) {
        return consumerProperties.getMaxReconsumeTimes() >= 0
                && msg.getReconsumeTimes() >= consumerProperties.getMaxReconsumeTimes();
    }

    @Override
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
