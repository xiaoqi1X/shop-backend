package com.hmall.seckill.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.domain.vo.SeckillOrderResultVO;
import com.hmall.seckill.service.SeckillResultService;
import com.hmall.seckill.service.SeckillResultPushService;
import com.hmall.seckill.support.SeckillMetricsLogger;
import com.hmall.seckill.websocket.SeckillResultWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillResultPushServiceImpl implements SeckillResultPushService {

    private final ObjectMapper objectMapper;
    private final SeckillResultService resultService;

    @Override
    public void push(SeckillRequestMessage requestMessage, SeckillStatus status, String message) {
        push(requestMessage, status, message, null);
    }

    @Override
    public void push(SeckillRequestMessage requestMessage, SeckillStatus status, String message, Long orderId) {
        long startedAt = SeckillMetricsLogger.start();
        if (requestMessage == null || requestMessage.getUserId() == null) {
            SeckillMetricsLogger.info("ws_push", "status", status == null ? null : status.name(), "delivered", false, "reason", "invalid_message", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return;
        }
        resultService.save(requestMessage, status, message, orderId);
        try {
            SeckillOrderResultVO result = new SeckillOrderResultVO();
            result.setSeckillOrderId(orderId);
            result.setRequestId(requestMessage.getRequestId());
            result.setSeckillId(requestMessage.getSeckillId());
            result.setItemId(requestMessage.getItemId());
            result.setStatus(status.name());
            result.setMessage(message == null ? status.getMessage() : message);
            result.setTotalFee(requestMessage.getTotalFee());
            String payload = objectMapper.writeValueAsString(result);
            boolean delivered = SeckillResultWebSocketHandler.send(requestMessage.getUserId(), payload);
            SeckillMetricsLogger.info("ws_push", "requestId", requestMessage.getRequestId(), "seckillId", requestMessage.getSeckillId(), "userId", requestMessage.getUserId(), "status", status.name(), "delivered", delivered, "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            if (!delivered) {
                log.debug("No websocket session for seckill result, userId={}, requestId={}",
                        requestMessage.getUserId(), requestMessage.getRequestId());
            }
        } catch (Exception e) {
            SeckillMetricsLogger.warn("ws_push", e, "requestId", requestMessage.getRequestId(), "seckillId", requestMessage.getSeckillId(), "userId", requestMessage.getUserId(), "status", status == null ? null : status.name(), "delivered", false, "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            log.warn("Failed to push seckill result, requestId={}", requestMessage.getRequestId(), e);
        }
    }
}
