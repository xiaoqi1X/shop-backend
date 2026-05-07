package com.hmall.seckill.service;

import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;

public interface SeckillResultPushService {

    void push(SeckillRequestMessage requestMessage, SeckillStatus status, String message);
}
