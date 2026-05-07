package com.hmall.trade.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hmall.trade.domain.enums.SeckillStatus;
import com.hmall.trade.domain.mq.SeckillOrderMessage;
import com.hmall.trade.domain.po.SeckillOrder;
import com.hmall.trade.mapper.SeckillOrderMapper;
import com.hmall.trade.mapper.SeckillStockMapper;
import com.hmall.trade.service.SeckillOrderFinalizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeckillOrderFinalizeServiceImpl implements SeckillOrderFinalizeService {

    private final SeckillOrderMapper orderMapper;
    private final SeckillStockMapper stockMapper;

    @Override
    @Transactional
    public void finalizeOrder(SeckillOrderMessage orderMessage) {
        Integer existing = orderMapper.selectCount(Wrappers.<SeckillOrder>lambdaQuery()
                .eq(SeckillOrder::getUserId, orderMessage.getUserId())
                .eq(SeckillOrder::getSeckillId, orderMessage.getSeckillId()));
        if (existing != null && existing > 0) {
            return;
        }

        SeckillOrder order = new SeckillOrder()
                .setId(orderMessage.getOrderId())
                .setRequestId(orderMessage.getRequestId())
                .setSeckillId(orderMessage.getSeckillId())
                .setItemId(orderMessage.getItemId())
                .setUserId(orderMessage.getUserId())
                .setNum(orderMessage.getNum())
                .setSeckillPrice(orderMessage.getSeckillPrice())
                .setTotalFee(orderMessage.getTotalFee())
                .setStatus(SeckillStatus.SUCCESS.name())
                .setResultCode(SeckillStatus.SUCCESS.name())
                .setFailureReason(null);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            return;
        }

        int updated = stockMapper.deductStock(orderMessage.getSeckillId(), orderMessage.getNum());
        if (updated == 0) {
            throw new IllegalStateException("Failed to deduct database seckill stock, requestId=" + orderMessage.getRequestId());
        }
    }
}
