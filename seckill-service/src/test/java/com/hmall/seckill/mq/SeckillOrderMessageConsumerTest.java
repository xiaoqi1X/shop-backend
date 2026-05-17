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
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillOrderMessageConsumerTest {

    @Mock
    private SeckillOrderFinalizeService finalizeService;
    @Mock
    private SeckillQuotaService quotaService;
    @Mock
    private SeckillResultPushService resultPushService;
    @Mock
    private SeckillOrderOutboxService outboxService;

    private SeckillOrderMessageConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SeckillAsyncProperties properties = new SeckillAsyncProperties();
        properties.getRocketmq().getOrderConsumer().setMaxReconsumeTimes(3);
        objectMapper = new ObjectMapper();
        consumer = new SeckillOrderMessageConsumer(properties, objectMapper, finalizeService, quotaService, resultPushService, outboxService);
    }

    @Test
    void handleMessageShouldPushSuccessWhenFinalized() throws Exception {
        when(finalizeService.finalizeOrder(any(SeckillOrderMessage.class))).thenReturn(true);

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(message(), 0));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(resultPushService).push(any(SeckillRequestMessage.class), eq(SeckillStatus.SUCCESS), eq(null), eq(123L));
        verify(outboxService).markFinalized("req-1");
    }

    @Test
    void handleMessageShouldPushSuccessForDuplicateFinalization() throws Exception {
        when(finalizeService.finalizeOrder(any(SeckillOrderMessage.class))).thenReturn(false);

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(message(), 0));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(resultPushService).push(any(SeckillRequestMessage.class), eq(SeckillStatus.SUCCESS), eq("Order already finalized"), eq(123L));
        verify(outboxService).markFinalized("req-1");
    }

    @Test
    void handleMessageShouldReleaseQuotaAndConsumeWhenDatabaseStockDeductFails() throws Exception {
        doThrow(new SeckillStockDeductFailedException("req-1"))
                .when(finalizeService).finalizeOrder(any(SeckillOrderMessage.class));

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(message(), 0));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(quotaService).release(any(SeckillRequestMessage.class));
        verify(resultPushService).push(any(SeckillRequestMessage.class), eq(SeckillStatus.SOLD_OUT), eq("Sold out"));
        verify(outboxService).markFailed("req-1", "Database seckill stock exhausted");
    }

    @Test
    void handleMessageShouldRetryGenericExceptionBeforeFinalRetry() throws Exception {
        doThrow(new RuntimeException("db unavailable"))
                .when(finalizeService).finalizeOrder(any(SeckillOrderMessage.class));

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(message(), 2));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        verify(resultPushService, never()).push(any(SeckillRequestMessage.class), eq(SeckillStatus.FAILED), any());
    }

    @Test
    void handleMessageShouldWriteFailedResultOnFinalRetryForGenericException() throws Exception {
        doThrow(new RuntimeException("db unavailable"))
                .when(finalizeService).finalizeOrder(any(SeckillOrderMessage.class));

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(message(), 3));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        verify(resultPushService).push(any(SeckillRequestMessage.class), eq(SeckillStatus.FAILED), eq("Seckill order finalization failed after retries"));
        verify(outboxService).markFailed("req-1", "Seckill order finalization failed after retries");
    }

    private MessageExt messageExt(SeckillOrderMessage orderMessage, int reconsumeTimes) throws Exception {
        MessageExt msg = new MessageExt();
        msg.setMsgId("msg-1");
        msg.setBody(objectMapper.writeValueAsBytes(orderMessage));
        msg.setReconsumeTimes(reconsumeTimes);
        return msg;
    }

    private SeckillOrderMessage message() {
        return new SeckillOrderMessage()
                .setOrderId(123L)
                .setRequestId("req-1")
                .setSeckillId(1L)
                .setItemId(317578L)
                .setUserId(100L)
                .setNum(1)
                .setSeckillPrice(9900)
                .setTotalFee(9900);
    }
}
