package com.hmall.seckill.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.exception.SeckillStockDeductFailedException;
import com.hmall.seckill.domain.mq.SeckillOrderMessage;
import com.hmall.seckill.domain.po.SeckillOrder;
import com.hmall.seckill.mapper.SeckillOrderMapper;
import com.hmall.seckill.mapper.SeckillStockMapper;
import com.hmall.seckill.service.SeckillOrderFinalizeService;
import com.hmall.seckill.support.SeckillMetricsLogger;
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
    public boolean finalizeOrder(SeckillOrderMessage orderMessage) {
        long startedAt = SeckillMetricsLogger.start();
        long duplicateCheckStartedAt = SeckillMetricsLogger.start();
        Integer existing = orderMapper.selectCount(Wrappers.<SeckillOrder>lambdaQuery()
                .eq(SeckillOrder::getUserId, orderMessage.getUserId())
                .eq(SeckillOrder::getSeckillId, orderMessage.getSeckillId()));
        long duplicateCheckMs = SeckillMetricsLogger.elapsedMs(duplicateCheckStartedAt);
        if (existing != null && existing > 0) {
            SeckillMetricsLogger.info("order_finalize", "requestId", orderMessage.getRequestId(), "orderId", orderMessage.getOrderId(), "seckillId", orderMessage.getSeckillId(), "userId", orderMessage.getUserId(), "result", "DUPLICATE_EXISTING", "duplicateCheckMs", duplicateCheckMs, "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return false;
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
            long insertStartedAt = SeckillMetricsLogger.start();
            orderMapper.insert(order);
            long insertMs = SeckillMetricsLogger.elapsedMs(insertStartedAt);
            long stockStartedAt = SeckillMetricsLogger.start();
            int updated = stockMapper.deductStock(orderMessage.getSeckillId(), orderMessage.getNum());
            long stockMs = SeckillMetricsLogger.elapsedMs(stockStartedAt);
            if (updated == 0) {
                SeckillMetricsLogger.info("order_finalize", "requestId", orderMessage.getRequestId(), "orderId", orderMessage.getOrderId(), "seckillId", orderMessage.getSeckillId(), "userId", orderMessage.getUserId(), "result", "STOCK_DEDUCT_FAILED", "duplicateCheckMs", duplicateCheckMs, "insertMs", insertMs, "stockMs", stockMs, "affectedRows", updated, "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
                throw new SeckillStockDeductFailedException(orderMessage.getRequestId());
            }
            SeckillMetricsLogger.info("order_finalize", "requestId", orderMessage.getRequestId(), "orderId", orderMessage.getOrderId(), "seckillId", orderMessage.getSeckillId(), "userId", orderMessage.getUserId(), "result", "SUCCESS", "duplicateCheckMs", duplicateCheckMs, "insertMs", insertMs, "stockMs", stockMs, "affectedRows", updated, "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
        } catch (DuplicateKeyException e) {
            SeckillMetricsLogger.info("order_finalize", "requestId", orderMessage.getRequestId(), "orderId", orderMessage.getOrderId(), "seckillId", orderMessage.getSeckillId(), "userId", orderMessage.getUserId(), "result", "DUPLICATE_KEY", "duplicateCheckMs", duplicateCheckMs, "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return false;
        }
        return true;
    }
}
