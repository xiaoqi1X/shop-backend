package com.hmall.seckill.service;

import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.domain.vo.SeckillOrderResultVO;

public interface SeckillResultService {

    void save(SeckillRequestMessage requestMessage, SeckillStatus status, String message);

    void save(SeckillRequestMessage requestMessage, SeckillStatus status, String message, Long orderId);

    SeckillOrderResultVO queryByRequestId(String requestId);
}
