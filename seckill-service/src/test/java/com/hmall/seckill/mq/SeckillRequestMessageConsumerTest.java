package com.hmall.seckill.mq;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.enums.SeckillQuotaResult;
import com.hmall.seckill.domain.mq.SeckillOrderMessage;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.service.SeckillQuotaService;
import com.hmall.seckill.service.SeckillResultPushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

    private SeckillRequestMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SeckillRequestMessageConsumer(
                new SeckillAsyncProperties(),
                null,
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

    private SeckillRequestMessage message() {
        return new SeckillRequestMessage()
                .setRequestId("req-1")
                .setSeckillId(1L)
                .setItemId(317578L)
                .setUserId(100L)
                .setNum(1)
                .setSeckillPrice(9900)
                .setTotalFee(9900)
                .setCreateTime(LocalDateTime.now());
    }
}
