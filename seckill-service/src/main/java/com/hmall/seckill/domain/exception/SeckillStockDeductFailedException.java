package com.hmall.seckill.domain.exception;

public class SeckillStockDeductFailedException extends RuntimeException {

    public SeckillStockDeductFailedException(String requestId) {
        super("Failed to deduct database seckill stock, requestId=" + requestId);
    }
}
