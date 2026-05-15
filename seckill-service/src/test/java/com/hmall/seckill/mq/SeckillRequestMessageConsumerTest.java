package com.hmall.seckill.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.enums.SeckillQuotaResult;
import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.mq.SeckillOrderMessage;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.service.SeckillQuotaService;
import com.hmall.seckill.service.SeckillResultPushService;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillRequestMessageConsumerTest {

    @Mock
    private SeckillQuotaService quotaService;
    @Mock
    private SeckillOrderMessageProducer orderMessageProducer;
    @Mock
    private IdentifierGenerator identifierGenerator;
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
                orderMessageProducer,
                identifierGenerator,
                resultPushService
        );
    }

    @Test
    void shouldBuildOrderMessageFromRequestMessage() throws Exception {
        when(identifierGenerator.nextId(any())).thenReturn(123L);

        SeckillOrderMessage orderMessage = invokeBuildOrderMessage(message());

        assertThat(orderMessage.getOrderId()).isEqualTo(123L);
        assertThat(orderMessage.getRequestId()).isEqualTo("req-1");
        assertThat(orderMessage.getUserId()).isEqualTo(100L);
        assertThat(orderMessage.getTotalFee()).isEqualTo(9900);
    }

    @Test
    void shouldReleaseQuotaWhenOrderMessageSendFails() {
        SeckillRequestMessage requestMessage = message();
        when(quotaService.tryAcquire(requestMessage)).thenReturn(SeckillQuotaResult.SUCCESS);
        when(identifierGenerator.nextId(any())).thenReturn(123L);
        doThrow(new RuntimeException("mq unavailable")).when(orderMessageProducer).send(any(SeckillOrderMessage.class));

        assertThatThrownBy(() -> handleQuotaSuccessLikeConsumer(requestMessage))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("mq unavailable");

        verify(quotaService).release(requestMessage);
        ArgumentCaptor<SeckillOrderMessage> captor = ArgumentCaptor.forClass(SeckillOrderMessage.class);
        verify(orderMessageProducer).send(captor.capture());
        assertThat(captor.getValue().getRequestId()).isEqualTo("req-1");
    }

    @Test
    void handleMessageShouldPushQuotaSuccessWhenOrderMessageSent() throws Exception {
        SeckillRequestMessage requestMessage = message();
        when(quotaService.tryAcquire(any(SeckillRequestMessage.class))).thenReturn(SeckillQuotaResult.SUCCESS);
        when(identifierGenerator.nextId(any())).thenReturn(123L);

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(requestMessage, 0));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(orderMessageProducer).send(any(SeckillOrderMessage.class));
        verify(resultPushService).push(any(SeckillRequestMessage.class), eq(SeckillStatus.QUOTA_SUCCESS), eq(null));
    }

    @Test
    void handleMessageShouldReleaseQuotaAndRetryWhenOrderMessageSendFailsBeforeFinalRetry() throws Exception {
        SeckillRequestMessage requestMessage = message();
        when(quotaService.tryAcquire(any(SeckillRequestMessage.class))).thenReturn(SeckillQuotaResult.SUCCESS);
        when(identifierGenerator.nextId(any())).thenReturn(123L);
        doThrow(new RuntimeException("mq unavailable")).when(orderMessageProducer).send(any(SeckillOrderMessage.class));

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(requestMessage, 2));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        verify(quotaService).release(any(SeckillRequestMessage.class));
        verify(resultPushService, never()).push(any(SeckillRequestMessage.class), eq(SeckillStatus.FAILED), any());
    }

    @Test
    void handleMessageShouldWriteFailedResultOnFinalRetryWhenOrderMessageSendFails() throws Exception {
        SeckillRequestMessage requestMessage = message();
        when(quotaService.tryAcquire(any(SeckillRequestMessage.class))).thenReturn(SeckillQuotaResult.SUCCESS);
        when(identifierGenerator.nextId(any())).thenReturn(123L);
        doThrow(new RuntimeException("mq unavailable")).when(orderMessageProducer).send(any(SeckillOrderMessage.class));

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(requestMessage, 3));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        verify(quotaService).release(any(SeckillRequestMessage.class));
        verify(resultPushService).push(any(SeckillRequestMessage.class), eq(SeckillStatus.FAILED), eq("Failed to enqueue seckill order after retries"));
    }

    @Test
    void handleMessageShouldConsumeBusinessRejectionWithoutRetry() throws Exception {
        SeckillRequestMessage requestMessage = message();
        when(quotaService.tryAcquire(any(SeckillRequestMessage.class))).thenReturn(SeckillQuotaResult.SOLD_OUT);

        ConsumeConcurrentlyStatus status = consumer.handleMessage(messageExt(requestMessage, 0));

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(resultPushService).push(any(SeckillRequestMessage.class), eq(SeckillStatus.SOLD_OUT), eq(null));
        verify(orderMessageProducer, never()).send(any(SeckillOrderMessage.class));
    }

    private void handleQuotaSuccessLikeConsumer(SeckillRequestMessage requestMessage) throws Exception {
        SeckillQuotaResult result = quotaService.tryAcquire(requestMessage);
        if (result == SeckillQuotaResult.SUCCESS) {
            try {
                orderMessageProducer.send(invokeBuildOrderMessage(requestMessage));
            } catch (RuntimeException e) {
                quotaService.release(requestMessage);
                throw e;
            }
        }
    }

    private SeckillOrderMessage invokeBuildOrderMessage(SeckillRequestMessage requestMessage) throws Exception {
        Method method = SeckillRequestMessageConsumer.class.getDeclaredMethod("buildOrderMessage", SeckillRequestMessage.class);
        method.setAccessible(true);
        return (SeckillOrderMessage) method.invoke(consumer, requestMessage);
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
}
