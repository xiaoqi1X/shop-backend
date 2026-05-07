package com.hmall.seckill.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.domain.vo.SeckillOrderResultVO;
import com.hmall.seckill.service.SeckillResultPushService;
import com.hmall.seckill.websocket.SeckillResultWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillResultPushServiceImpl implements SeckillResultPushService {

    private final ObjectMapper objectMapper;

    @Override
    public void push(SeckillRequestMessage requestMessage, SeckillStatus status, String message) {
        if (requestMessage == null || requestMessage.getUserId() == null) {
            return;
        }
        try {
            SeckillOrderResultVO result = new SeckillOrderResultVO();
            result.setRequestId(requestMessage.getRequestId());
            result.setSeckillId(requestMessage.getSeckillId());
            result.setItemId(requestMessage.getItemId());
            result.setStatus(status.name());
            result.setMessage(message == null ? status.getMessage() : message);
            result.setTotalFee(requestMessage.getTotalFee());
            String payload = objectMapper.writeValueAsString(result);
            boolean delivered = SeckillResultWebSocketHandler.send(requestMessage.getUserId(), payload);
            if (!delivered) {
                log.debug("No websocket session for seckill result, userId={}, requestId={}",
                        requestMessage.getUserId(), requestMessage.getRequestId());
            }
        } catch (Exception e) {
            log.warn("Failed to push seckill result, requestId={}", requestMessage.getRequestId(), e);
        }
    }
}
