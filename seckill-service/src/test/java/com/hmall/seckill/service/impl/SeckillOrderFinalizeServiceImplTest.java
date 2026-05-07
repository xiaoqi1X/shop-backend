package com.hmall.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmall.seckill.domain.exception.SeckillStockDeductFailedException;
import com.hmall.seckill.domain.mq.SeckillOrderMessage;
import com.hmall.seckill.domain.po.SeckillOrder;
import com.hmall.seckill.mapper.SeckillOrderMapper;
import com.hmall.seckill.mapper.SeckillStockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillOrderFinalizeServiceImplTest {

    @Mock
    private SeckillOrderMapper orderMapper;
    @Mock
    private SeckillStockMapper stockMapper;

    private SeckillOrderFinalizeServiceImpl finalizeService;

    @BeforeEach
    void setUp() {
        finalizeService = new SeckillOrderFinalizeServiceImpl(orderMapper, stockMapper);
    }

    @Test
    void finalizeOrderShouldInsertOrderAndDeductStock() {
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0);
        when(orderMapper.insert(any(SeckillOrder.class))).thenReturn(1);
        when(stockMapper.deductStock(1L, 1)).thenReturn(1);

        boolean finalized = finalizeService.finalizeOrder(message());

        assertThat(finalized).isTrue();
        ArgumentCaptor<SeckillOrder> captor = ArgumentCaptor.forClass(SeckillOrder.class);
        verify(orderMapper).insert(captor.capture());
        SeckillOrder order = captor.getValue();
        assertThat(order.getId()).isEqualTo(123L);
        assertThat(order.getRequestId()).isEqualTo("req-1");
        assertThat(order.getUserId()).isEqualTo(100L);
        assertThat(order.getStatus()).isEqualTo("SUCCESS");
        assertThat(order.getResultCode()).isEqualTo("SUCCESS");
        verify(stockMapper).deductStock(1L, 1);
    }

    @Test
    void finalizeOrderShouldIgnoreDuplicateUserOrder() {
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1);

        boolean finalized = finalizeService.finalizeOrder(message());

        assertThat(finalized).isFalse();
        verify(orderMapper, never()).insert(any());
        verify(stockMapper, never()).deductStock(any(), any());
    }

    @Test
    void finalizeOrderShouldRetryWhenDatabaseStockCannotBeDeducted() {
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0);
        when(orderMapper.insert(any(SeckillOrder.class))).thenReturn(1);
        when(stockMapper.deductStock(1L, 1)).thenReturn(0);

        assertThatThrownBy(() -> finalizeService.finalizeOrder(message()))
                .isInstanceOf(SeckillStockDeductFailedException.class)
                .hasMessageContaining("Failed to deduct database seckill stock");
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
                .setTotalFee(9900)
                .setCreateTime(LocalDateTime.now());
    }
}
