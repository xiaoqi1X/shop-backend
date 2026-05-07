package com.hmall.seckill.service;

import com.hmall.seckill.domain.mq.SeckillOrderMessage;

public interface SeckillOrderFinalizeService {

    boolean finalizeOrder(SeckillOrderMessage orderMessage);
}
