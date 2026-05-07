package com.hmall.trade.service;

import com.hmall.trade.domain.enums.SeckillStatus;
import com.hmall.trade.domain.mq.SeckillRequestMessage;

public interface SeckillResultPushService {

    void push(SeckillRequestMessage requestMessage, SeckillStatus status, String message);
}
