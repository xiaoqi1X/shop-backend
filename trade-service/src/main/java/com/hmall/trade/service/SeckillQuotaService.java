package com.hmall.trade.service;

import com.hmall.trade.domain.enums.SeckillQuotaResult;
import com.hmall.trade.domain.mq.SeckillRequestMessage;

public interface SeckillQuotaService {

    SeckillQuotaResult tryAcquire(SeckillRequestMessage requestMessage);

    void release(SeckillRequestMessage requestMessage);
}
