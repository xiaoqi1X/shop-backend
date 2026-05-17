package com.hmall.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.enums.SeckillQuotaResult;
import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.mq.SeckillOrderMessage;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
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
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@DependsOn("seckillActivityCacheServiceImpl")
@RequiredArgsConstructor
public class SeckillRequestMessageConsumer implements InitializingBean, DisposableBean {

    private final SeckillAsyncProperties properties;
    private final ObjectMapper objectMapper;
    private final SeckillQuotaService quotaService;
    private final SeckillOrderOutboxService outboxService;
    private final SeckillResultPushService resultPushService;

    private DefaultMQPushConsumer consumer;

    @Override
    public void afterPropertiesSet() throws Exception {
        SeckillAsyncProperties.Rocketmq rocketmq = properties.getRocketmq();

        consumer = new DefaultMQPushConsumer(rocketmq.getRequestConsumerGroup());
        consumer.setNamesrvAddr(rocketmq.getNameServer());
        applyConsumerProperties(rocketmq.getRequestConsumer());
        log.info("Starting seckill request consumer rocketmq settings, consumeThreadMin={}, consumeThreadMax={}, consumeMessageBatchMaxSize={}, consumeTimeoutMinutes={}, maxReconsumeTimes={}",
                rocketmq.getRequestConsumer().getConsumeThreadMin(),
                rocketmq.getRequestConsumer().getConsumeThreadMax(),
                rocketmq.getRequestConsumer().getConsumeMessageBatchMaxSize(),
                rocketmq.getRequestConsumer().getConsumeTimeoutMinutes(),
                rocketmq.getRequestConsumer().getMaxReconsumeTimes());
        consumer.subscribe(rocketmq.getRequestTopic(), rocketmq.getRequestTag());
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
        SeckillRequestMessage requestMessage = null;
        try {
            requestMessage = objectMapper.readValue(
                    new String(msg.getBody(), StandardCharsets.UTF_8),
                    SeckillRequestMessage.class
            );
            SeckillOrderMessage existingOrderMessage = outboxService.findMessageByRequestId(requestMessage.getRequestId()).orElse(null);
            if (existingOrderMessage != null) {
                resultPushService.push(requestMessage, SeckillStatus.QUOTA_SUCCESS, null);
                SeckillMetricsLogger.info("request_consume", "requestId", requestMessage.getRequestId(), "seckillId", requestMessage.getSeckillId(), "userId", requestMessage.getUserId(), "msgId", msg.getMsgId(), "quotaResult", "OUTBOX_EXISTS", "quotaMs", 0L, "orderMqMs", 0L, "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
            long quotaStartedAt = SeckillMetricsLogger.start();
            SeckillQuotaResult result = quotaService.tryAcquire(requestMessage);
            long quotaMs = SeckillMetricsLogger.elapsedMs(quotaStartedAt);
            long orderMqMs = 0L;
            if (result == SeckillQuotaResult.SUCCESS) {
                outboxService.createOrGetPendingMessage(requestMessage);
                resultPushService.push(requestMessage, SeckillStatus.QUOTA_SUCCESS, null);
            } else if (result == SeckillQuotaResult.DUPLICATE) {
                resultPushService.push(requestMessage, SeckillStatus.DUPLICATE_ORDER, null);
            } else if (result == SeckillQuotaResult.SOLD_OUT) {
                resultPushService.push(requestMessage, SeckillStatus.SOLD_OUT, null);
            } else {
                resultPushService.push(requestMessage, result == SeckillQuotaResult.NOT_READY ? SeckillStatus.NOT_READY : SeckillStatus.FAILED, null);
            }
            SeckillMetricsLogger.info("request_consume", "requestId", requestMessage.getRequestId(), "seckillId", requestMessage.getSeckillId(), "userId", requestMessage.getUserId(), "msgId", msg.getMsgId(), "quotaResult", result, "quotaMs", quotaMs, "orderMqMs", orderMqMs, "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            log.debug("Consumed seckill request, requestId={}, result={}, msgId={}",
                    requestMessage.getRequestId(), result, msg.getMsgId());
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (Exception e) {
            if (requestMessage != null && isFinalRetry(msg, properties.getRocketmq().getRequestConsumer())) {
                resultPushService.push(requestMessage, SeckillStatus.FAILED, "Seckill request failed after retries");
            }
            SeckillMetricsLogger.warn("request_consume", e, "requestId", requestMessage == null ? null : requestMessage.getRequestId(), "seckillId", requestMessage == null ? null : requestMessage.getSeckillId(), "userId", requestMessage == null ? null : requestMessage.getUserId(), "msgId", msg.getMsgId(), "result", "EXCEPTION", "finalRetry", isFinalRetry(msg, properties.getRocketmq().getRequestConsumer()), "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            log.error("Failed to consume seckill request message, msgId={}", msg.getMsgId(), e);
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
