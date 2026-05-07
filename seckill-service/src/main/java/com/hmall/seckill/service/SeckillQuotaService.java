package com.hmall.seckill.service;

import com.hmall.seckill.domain.enums.SeckillQuotaResult;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;

public interface SeckillQuotaService {

    SeckillQuotaResult tryAcquire(SeckillRequestMessage requestMessage);

    void release(SeckillRequestMessage requestMessage);
}
