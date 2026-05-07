package com.hmall.trade.service;

import com.hmall.trade.domain.mq.SeckillOrderMessage;

public interface SeckillOrderFinalizeService {

    void finalizeOrder(SeckillOrderMessage orderMessage);
}
