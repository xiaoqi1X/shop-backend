package com.hmall.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.mq.SeckillOrderMessage;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.domain.po.SeckillOrderOutbox;
import com.hmall.seckill.mapper.SeckillOrderOutboxMapper;
import com.hmall.seckill.mq.SeckillOrderMessageProducer;
import com.hmall.seckill.service.SeckillQuotaService;
import com.hmall.seckill.service.SeckillResultPushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillOrderOutboxServiceImplTest {

    @Mock
    private SeckillOrderOutboxMapper outboxMapper;
    @Mock
    private IdentifierGenerator identifierGenerator;
    @Mock
    private SeckillOrderMessageProducer orderMessageProducer;
    @Mock
    private SeckillQuotaService quotaService;
    @Mock
    private SeckillResultPushService resultPushService;

    private SeckillOrderOutboxServiceImpl outboxService;
    private SeckillAsyncProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SeckillAsyncProperties();
        properties.getOutbox().setMaxRetryCount(5);
        properties.getOutbox().setRetryDelaySeconds(5);
        outboxService = new SeckillOrderOutboxServiceImpl(
                outboxMapper,
                identifierGenerator,
                orderMessageProducer,
                properties,
                quotaService,
                resultPushService
        );
    }

    @Test
    void createOrGetPendingMessageShouldInsertOutboxAndReturnStableOrderMessage() {
        when(outboxMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(identifierGenerator.nextId(any())).thenReturn(999L, 123L);

        SeckillOrderMessage orderMessage = outboxService.createOrGetPendingMessage(requestMessage());

        assertThat(orderMessage.getOrderId()).isEqualTo(123L);
        assertThat(orderMessage.getRequestId()).isEqualTo("req-1");
        ArgumentCaptor<SeckillOrderOutbox> captor = ArgumentCaptor.forClass(SeckillOrderOutbox.class);
        verify(outboxMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("NEW");
        assertThat(captor.getValue().getMaxRetryCount()).isEqualTo(5);
    }

    @Test
    void createOrGetPendingMessageShouldReuseExistingOutbox() {
        when(outboxMapper.selectOne(any(Wrapper.class))).thenReturn(outbox(0, 5));

        SeckillOrderMessage orderMessage = outboxService.createOrGetPendingMessage(requestMessage());

        assertThat(orderMessage.getOrderId()).isEqualTo(123L);
        verify(outboxMapper, never()).insert(any(SeckillOrderOutbox.class));
    }

    @Test
    void sendAndMarkShouldSendMqAndMarkSent() {
        boolean sent = outboxService.sendAndMark(orderMessage());

        assertThat(sent).isTrue();
        verify(orderMessageProducer).send(any(SeckillOrderMessage.class));
        verify(outboxMapper).update(eq(null), any(Wrapper.class));
    }

    @Test
    void sendAndMarkShouldKeepQuotaWhenSendFailsBeforeMaxRetry() {
        when(outboxMapper.selectOne(any(Wrapper.class))).thenReturn(outbox(0, 5));
        doThrow(new RuntimeException("mq unavailable")).when(orderMessageProducer).send(any(SeckillOrderMessage.class));

        boolean sent = outboxService.sendAndMark(orderMessage());

        assertThat(sent).isFalse();
        verify(quotaService, never()).release(any(SeckillRequestMessage.class));
        verify(resultPushService, never()).push(any(SeckillRequestMessage.class), eq(SeckillStatus.FAILED), any());
        verify(outboxMapper).update(eq(null), any(Wrapper.class));
    }

    @Test
    void sendAndMarkShouldReleaseQuotaWhenRetryExhausted() {
        when(outboxMapper.selectOne(any(Wrapper.class))).thenReturn(outbox(4, 5));
        doThrow(new RuntimeException("mq unavailable")).when(orderMessageProducer).send(any(SeckillOrderMessage.class));

        boolean sent = outboxService.sendAndMark(orderMessage());

        assertThat(sent).isFalse();
        verify(quotaService).release(any(SeckillRequestMessage.class));
        verify(resultPushService).push(any(SeckillRequestMessage.class), eq(SeckillStatus.FAILED), eq("Failed to enqueue seckill order after outbox retries"));
    }

    private SeckillRequestMessage requestMessage() {
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

    private SeckillOrderMessage orderMessage() {
        return new SeckillOrderMessage()
                .setOrderId(123L)
                .setRequestId("req-1")
                .setSeckillId(1L)
                .setItemId(317578L)
                .setUserId(100L)
                .setNum(1)
                .setSeckillPrice(9900)
                .setTotalFee(9900)
                .setCreateTime(LocalDateTime.now());
    }

    private SeckillOrderOutbox outbox(int retryCount, int maxRetryCount) {
        return new SeckillOrderOutbox()
                .setId(999L)
                .setOrderId(123L)
                .setRequestId("req-1")
                .setSeckillId(1L)
                .setItemId(317578L)
                .setUserId(100L)
                .setNum(1)
                .setSeckillPrice(9900)
                .setTotalFee(9900)
                .setStatus("NEW")
                .setRetryCount(retryCount)
                .setMaxRetryCount(maxRetryCount)
                .setCreateTime(LocalDateTime.now());
    }
}
