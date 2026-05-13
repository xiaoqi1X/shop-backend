package com.hmall.seckill.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.ForbiddenException;
import com.hmall.common.utils.UserContext;
import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.domain.redis.SeckillOrderResultRecord;
import com.hmall.seckill.domain.vo.SeckillOrderResultVO;
import com.hmall.seckill.service.SeckillResultService;
import com.hmall.seckill.support.SeckillMetricsLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillResultServiceImpl implements SeckillResultService {

    private static final long DEFAULT_RESULT_TTL_SECONDS = 7200L;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SeckillAsyncProperties properties;

    @Override
    public void save(SeckillRequestMessage requestMessage, SeckillStatus status, String message) {
        save(requestMessage, status, message, null);
    }

    @Override
    public void save(SeckillRequestMessage requestMessage, SeckillStatus status, String message, Long orderId) {
        long startedAt = SeckillMetricsLogger.start();
        if (requestMessage == null || !StringUtils.hasText(requestMessage.getRequestId())
                || requestMessage.getUserId() == null || status == null) {
            SeckillMetricsLogger.info("result_save", "status", status == null ? null : status.name(), "result", "SKIPPED", "reason", "invalid_message", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return;
        }
        try {
            SeckillOrderResultRecord record = buildRecord(requestMessage, status, message, orderId);
            String payload = objectMapper.writeValueAsString(record);
            stringRedisTemplate.opsForValue().set(key(requestMessage.getRequestId()), payload, resultTtl());
            SeckillMetricsLogger.info("result_save", "requestId", requestMessage.getRequestId(), "seckillId", requestMessage.getSeckillId(), "userId", requestMessage.getUserId(), "status", status.name(), "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
        } catch (Exception e) {
            SeckillMetricsLogger.warn("result_save", e, "requestId", requestMessage.getRequestId(), "seckillId", requestMessage.getSeckillId(), "userId", requestMessage.getUserId(), "status", status.name(), "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            log.warn("Failed to save seckill result, requestId={}", requestMessage.getRequestId(), e);
        }
    }

    @Override
    public SeckillOrderResultVO queryByRequestId(String requestId) {
        long startedAt = SeckillMetricsLogger.start();
        Long userId = UserContext.getUser();
        if (userId == null) {
            SeckillMetricsLogger.info("result_query", "requestId", requestId, "result", "MISSING_USER", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            throw new ForbiddenException("Missing login user");
        }
        if (!StringUtils.hasText(requestId)) {
            SeckillMetricsLogger.info("result_query", "requestId", requestId, "userId", userId, "result", "INVALID_REQUEST_ID", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            throw new BadRequestException("requestId must not be blank");
        }
        try {
            String payload = stringRedisTemplate.opsForValue().get(key(requestId));
            if (!StringUtils.hasText(payload)) {
                SeckillMetricsLogger.info("result_query", "requestId", requestId, "userId", userId, "result", "PROCESSING", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
                SeckillOrderResultVO processing = new SeckillOrderResultVO();
                processing.setRequestId(requestId);
                processing.setStatus(SeckillStatus.ACCEPTED.name());
                processing.setMessage("Request is still being processed");
                return processing;
            }
            SeckillOrderResultRecord record = objectMapper.readValue(payload, SeckillOrderResultRecord.class);
            if (!userId.equals(record.getUserId())) {
                SeckillMetricsLogger.info("result_query", "requestId", requestId, "userId", userId, "ownerId", record.getUserId(), "result", "FORBIDDEN", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
                throw new ForbiddenException("Cannot query another user's seckill result");
            }
            SeckillMetricsLogger.info("result_query", "requestId", requestId, "userId", userId, "status", record.getStatus(), "result", "FOUND", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return toResult(record);
        } catch (ForbiddenException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            SeckillMetricsLogger.warn("result_query", e, "requestId", requestId, "userId", userId, "result", "EXCEPTION", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            throw new BadRequestException("Failed to query seckill result");
        }
    }

    private SeckillOrderResultRecord buildRecord(SeckillRequestMessage requestMessage, SeckillStatus status, String message, Long orderId) {
        SeckillOrderResultRecord record = new SeckillOrderResultRecord();
        record.setSeckillOrderId(orderId);
        record.setRequestId(requestMessage.getRequestId());
        record.setSeckillId(requestMessage.getSeckillId());
        record.setItemId(requestMessage.getItemId());
        record.setUserId(requestMessage.getUserId());
        record.setStatus(status.name());
        record.setMessage(message == null ? status.getMessage() : message);
        record.setTotalFee(requestMessage.getTotalFee());
        return record;
    }

    private SeckillOrderResultVO toResult(SeckillOrderResultRecord record) {
        SeckillOrderResultVO result = new SeckillOrderResultVO();
        result.setSeckillOrderId(record.getSeckillOrderId());
        result.setRequestId(record.getRequestId());
        result.setSeckillId(record.getSeckillId());
        result.setItemId(record.getItemId());
        result.setStatus(record.getStatus());
        result.setMessage(record.getMessage());
        result.setTotalFee(record.getTotalFee());
        return result;
    }

    private String key(String requestId) {
        return properties.getRedis().getResultKeyPrefix() + requestId;
    }

    private Duration resultTtl() {
        long seconds = properties.getRedis().getResultTtlSeconds();
        return Duration.ofSeconds(seconds > 0 ? seconds : DEFAULT_RESULT_TTL_SECONDS);
    }
}
