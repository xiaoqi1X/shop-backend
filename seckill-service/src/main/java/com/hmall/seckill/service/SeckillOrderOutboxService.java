package com.hmall.seckill.service;

import com.hmall.seckill.domain.mq.SeckillOrderMessage;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;

import java.util.Optional;

public interface SeckillOrderOutboxService {

    Optional<SeckillOrderMessage> findMessageByRequestId(String requestId);

    SeckillOrderMessage createOrGetPendingMessage(SeckillRequestMessage requestMessage);

    boolean sendAndMark(SeckillOrderMessage orderMessage);

    void retryDueMessages();

    void markFinalized(String requestId);

    void markFailed(String requestId, String reason);
}
