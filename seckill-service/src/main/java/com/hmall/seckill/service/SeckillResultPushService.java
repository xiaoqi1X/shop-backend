package com.hmall.seckill.service;

import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;

public interface SeckillResultPushService {

    void push(SeckillRequestMessage requestMessage, SeckillStatus status, String message);

    default void push(SeckillRequestMessage requestMessage, SeckillStatus status, String message, Long orderId) {
        push(requestMessage, status, message);
    }
}
