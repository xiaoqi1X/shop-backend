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
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillRequestMessageConsumerTest {

    @Mock
    private SeckillQuotaService quotaService;
    @Mock
    private SeckillOrderOutboxService outboxService;
    @Mock
    private SeckillResultPushService resultPushService;
    private SeckillAsyncProperties properties;
    private ObjectMapper objectMapper;

    private SeckillRequestMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        properties = new SeckillAsyncProperties();
        properties.getRocketmq().getRequestConsumer().setMaxReconsumeTimes(3);
        objectMapper = new ObjectMapper();
        consumer = new SeckillRequestMessageConsumer(
                properties,
                objectMapper,
                quotaService,
                outboxService,
                resultPushService
        );
    }

    @Test
    void handleMessageShouldPushQuotaSuccessWhenOutboxMessageSaved() throws Exception {
        SeckillRequestMessage requestMessage = message();
        SeckillOrderMessage orderMessage = orderMessage();
        when(quotaService.tryAcquire(any(SeckillRequestMessage.class))).thenReturn(SeckillQuotaResult.SUCCESS);
        when(outboxService.createOrGetPendingMessage(any(SeckillRequestMessage.class))).thenReturn(orderMessage);

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(requestMessage, 0));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(outboxService).createOrGetPendingMessage(any(SeckillRequestMessage.class));
        verify(outboxService, never()).sendAndMark(any(SeckillOrderMessage.class));
        verify(resultPushService).push(any(SeckillRequestMessage.class), eq(SeckillStatus.QUOTA_SUCCESS), eq(null));
    }

    @Test
    void handleMessageShouldNotReleaseQuotaAfterSavingOutbox() throws Exception {
        SeckillRequestMessage requestMessage = message();
        SeckillOrderMessage orderMessage = orderMessage();
        when(quotaService.tryAcquire(any(SeckillRequestMessage.class))).thenReturn(SeckillQuotaResult.SUCCESS);
        when(outboxService.createOrGetPendingMessage(any(SeckillRequestMessage.class))).thenReturn(orderMessage);

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(requestMessage, 2));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(quotaService, never()).release(any(SeckillRequestMessage.class));
        verify(outboxService, never()).sendAndMark(any(SeckillOrderMessage.class));
        verify(resultPushService, never()).push(any(SeckillRequestMessage.class), eq(SeckillStatus.FAILED), any());
        verify(resultPushService).push(any(SeckillRequestMessage.class), eq(SeckillStatus.QUOTA_SUCCESS), eq(null));
    }

    @Test
    void handleMessageShouldReuseExistingOutboxWithoutTryingQuotaAgain() throws Exception {
        SeckillRequestMessage requestMessage = message();
        SeckillOrderMessage orderMessage = orderMessage();
        when(outboxService.findMessageByRequestId("req-1")).thenReturn(Optional.of(orderMessage));

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(requestMessage, 3));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(quotaService, never()).tryAcquire(any(SeckillRequestMessage.class));
        verify(outboxService, never()).sendAndMark(any(SeckillOrderMessage.class));
        verify(resultPushService).push(any(SeckillRequestMessage.class), eq(SeckillStatus.QUOTA_SUCCESS), eq(null));
    }

    @Test
    void handleMessageShouldConsumeBusinessRejectionWithoutRetry() throws Exception {
        SeckillRequestMessage requestMessage = message();
        when(quotaService.tryAcquire(any(SeckillRequestMessage.class))).thenReturn(SeckillQuotaResult.SOLD_OUT);

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(requestMessage, 0));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(resultPushService).push(any(SeckillRequestMessage.class), eq(SeckillStatus.SOLD_OUT), eq(null));
        verify(outboxService, never()).createOrGetPendingMessage(any(SeckillRequestMessage.class));
    }

    private MessageExt messageExt(SeckillRequestMessage requestMessage, int reconsumeTimes) throws Exception {
        MessageExt msg = new MessageExt();
        msg.setMsgId("msg-1");
        msg.setBody(objectMapper.writeValueAsBytes(requestMessage));
        msg.setReconsumeTimes(reconsumeTimes);
        return msg;
    }

    private SeckillRequestMessage message() {
        return new SeckillRequestMessage()
                .setRequestId("req-1")
                .setSeckillId(1L)
                .setItemId(317578L)
                .setUserId(100L)
                .setNum(1)
                .setSeckillPrice(9900)
                .setTotalFee(9900);
    }

    private SeckillOrderMessage orderMessage() {
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
