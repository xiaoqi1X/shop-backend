package com.hmall.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.enums.SeckillOrderOutboxStatus;
import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.mq.SeckillOrderMessage;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.domain.po.SeckillOrderOutbox;
import com.hmall.seckill.mapper.SeckillOrderOutboxMapper;
import com.hmall.seckill.mq.SeckillOrderMessageProducer;
import com.hmall.seckill.service.SeckillOrderOutboxService;
import com.hmall.seckill.service.SeckillQuotaService;
import com.hmall.seckill.service.SeckillResultPushService;
import com.hmall.seckill.support.SeckillMetricsLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderOutboxServiceImpl implements SeckillOrderOutboxService {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_MAX_RETRY_COUNT = 5;
    private static final long DEFAULT_RETRY_DELAY_SECONDS = 5L;
    private static final int MAX_ERROR_LENGTH = 512;

    private final SeckillOrderOutboxMapper outboxMapper;
    private final IdentifierGenerator identifierGenerator;
    private final SeckillOrderMessageProducer orderMessageProducer;
    private final SeckillAsyncProperties properties;
    private final SeckillQuotaService quotaService;
    private final SeckillResultPushService resultPushService;

    @Override
    public Optional<SeckillOrderMessage> findMessageByRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return Optional.empty();
        }
        SeckillOrderOutbox outbox = findByRequestId(requestId);
        return Optional.ofNullable(outbox).map(this::toMessage);
    }

    @Override
    @Transactional
    public SeckillOrderMessage createOrGetPendingMessage(SeckillRequestMessage requestMessage) {
        SeckillOrderOutbox existing = findByRequestId(requestMessage.getRequestId());
        if (existing != null) {
            return toMessage(existing);
        }
        SeckillOrderOutbox outbox = toOutbox(requestMessage);
        try {
            outboxMapper.insert(outbox);
            SeckillMetricsLogger.info("outbox_create", "requestId", outbox.getRequestId(), "orderId", outbox.getOrderId(), "seckillId", outbox.getSeckillId(), "userId", outbox.getUserId(), "status", outbox.getStatus());
            return toMessage(outbox);
        } catch (DuplicateKeyException e) {
            SeckillOrderOutbox duplicate = findByRequestId(requestMessage.getRequestId());
            if (duplicate != null) {
                SeckillMetricsLogger.info("outbox_create", "requestId", duplicate.getRequestId(), "orderId", duplicate.getOrderId(), "status", duplicate.getStatus(), "result", "DUPLICATE_REUSED");
                return toMessage(duplicate);
            }
            throw e;
        }
    }

    @Override
    public boolean sendAndMark(SeckillOrderMessage orderMessage) {
        long startedAt = SeckillMetricsLogger.start();
        if (orderMessage == null || !StringUtils.hasText(orderMessage.getRequestId())) {
            SeckillMetricsLogger.info("outbox_send", "result", "SKIPPED", "reason", "invalid_message", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return false;
        }
        try {
            orderMessageProducer.send(orderMessage);
            markSent(orderMessage.getRequestId());
            SeckillMetricsLogger.info("outbox_send", "requestId", orderMessage.getRequestId(), "orderId", orderMessage.getOrderId(), "result", "SENT", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return true;
        } catch (RuntimeException e) {
            markSendFailed(orderMessage, e);
            SeckillMetricsLogger.warn("outbox_send", e, "requestId", orderMessage.getRequestId(), "orderId", orderMessage.getOrderId(), "result", "SEND_FAILED", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return false;
        }
    }

    @Override
    public void retryDueMessages() {
        if (!properties.getOutbox().isEnabled()) {
            return;
        }
        List<SeckillOrderOutbox> due = selectDueOutboxes();
        for (SeckillOrderOutbox outbox : due) {
            sendAndMark(toMessage(outbox));
        }
    }

    @Override
    public void markFinalized(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return;
        }
        updateStatus(requestId, SeckillOrderOutboxStatus.FINALIZED, null);
    }

    @Override
    public void markFailed(String requestId, String reason) {
        if (!StringUtils.hasText(requestId)) {
            return;
        }
        updateStatus(requestId, SeckillOrderOutboxStatus.FAILED, abbreviate(reason));
    }

    private void markSent(String requestId) {
        UpdateWrapper<SeckillOrderOutbox> wrapper = new UpdateWrapper<>();
        wrapper.eq("request_id", requestId)
                .in("status",
                        SeckillOrderOutboxStatus.NEW.name(),
                        SeckillOrderOutboxStatus.SEND_FAILED.name(),
                        SeckillOrderOutboxStatus.SENT.name())
                .set("status", SeckillOrderOutboxStatus.SENT.name())
                .set("last_error", null)
                .set("next_retry_time", null);
        outboxMapper.update(null, wrapper);
    }

    private void markSendFailed(SeckillOrderMessage orderMessage, RuntimeException e) {
        SeckillOrderOutbox outbox = findByRequestId(orderMessage.getRequestId());
        if (outbox == null) {
            return;
        }
        int retryCount = safeRetryCount(outbox) + 1;
        int maxRetryCount = safeMaxRetryCount(outbox);
        if (retryCount >= maxRetryCount) {
            markExhausted(outbox, e, retryCount);
            return;
        }
        UpdateWrapper<SeckillOrderOutbox> wrapper = new UpdateWrapper<>();
        wrapper.eq("request_id", outbox.getRequestId())
                .in("status",
                        SeckillOrderOutboxStatus.NEW.name(),
                        SeckillOrderOutboxStatus.SEND_FAILED.name(),
                        SeckillOrderOutboxStatus.SENT.name())
                .set("status", SeckillOrderOutboxStatus.SEND_FAILED.name())
                .set("retry_count", retryCount)
                .set("next_retry_time", LocalDateTime.now().plusSeconds(retryDelaySeconds()))
                .set("last_error", abbreviate(e.getMessage()));
        outboxMapper.update(null, wrapper);
    }

    private void markExhausted(SeckillOrderOutbox outbox, RuntimeException e, int retryCount) {
        UpdateWrapper<SeckillOrderOutbox> wrapper = new UpdateWrapper<>();
        wrapper.eq("request_id", outbox.getRequestId())
                .in("status",
                        SeckillOrderOutboxStatus.NEW.name(),
                        SeckillOrderOutboxStatus.SEND_FAILED.name(),
                        SeckillOrderOutboxStatus.SENT.name())
                .set("status", SeckillOrderOutboxStatus.FAILED.name())
                .set("retry_count", retryCount)
                .set("last_error", abbreviate(e.getMessage()));
        outboxMapper.update(null, wrapper);
        SeckillRequestMessage requestMessage = toRequestMessage(outbox);
        quotaService.release(requestMessage);
        resultPushService.push(requestMessage, SeckillStatus.FAILED, "Failed to enqueue seckill order after outbox retries");
    }

    private void updateStatus(String requestId, SeckillOrderOutboxStatus status, String error) {
        UpdateWrapper<SeckillOrderOutbox> wrapper = new UpdateWrapper<>();
        wrapper.eq("request_id", requestId)
                .set("status", status.name());
        if (StringUtils.hasText(error)) {
            wrapper.set("last_error", error);
        }
        outboxMapper.update(null, wrapper);
    }

    private List<SeckillOrderOutbox> selectDueOutboxes() {
        LocalDateTime now = LocalDateTime.now();
        QueryWrapper<SeckillOrderOutbox> wrapper = new QueryWrapper<>();
        wrapper.in("status", Arrays.asList(
                        SeckillOrderOutboxStatus.NEW.name(),
                        SeckillOrderOutboxStatus.SEND_FAILED.name()))
                .le("next_retry_time", now)
                .orderByAsc("next_retry_time")
                .last("LIMIT " + batchSize());
        return outboxMapper.selectList(wrapper);
    }

    private SeckillOrderOutbox findByRequestId(String requestId) {
        QueryWrapper<SeckillOrderOutbox> wrapper = new QueryWrapper<>();
        wrapper.eq("request_id", requestId).last("LIMIT 1");
        return outboxMapper.selectOne(wrapper);
    }

    private SeckillOrderOutbox toOutbox(SeckillRequestMessage requestMessage) {
        LocalDateTime now = LocalDateTime.now();
        return new SeckillOrderOutbox()
                .setId(identifierGenerator.nextId(requestMessage).longValue())
                .setOrderId(identifierGenerator.nextId(requestMessage).longValue())
                .setRequestId(requestMessage.getRequestId())
                .setSeckillId(requestMessage.getSeckillId())
                .setItemId(requestMessage.getItemId())
                .setUserId(requestMessage.getUserId())
                .setNum(requestMessage.getNum())
                .setSeckillPrice(requestMessage.getSeckillPrice())
                .setTotalFee(requestMessage.getTotalFee())
                .setStatus(SeckillOrderOutboxStatus.NEW.name())
                .setRetryCount(0)
                .setMaxRetryCount(maxRetryCount())
                .setNextRetryTime(now)
                .setCreateTime(requestMessage.getCreateTime() == null ? now : requestMessage.getCreateTime())
                .setUpdateTime(now);
    }

    private SeckillOrderMessage toMessage(SeckillOrderOutbox outbox) {
        return new SeckillOrderMessage()
                .setOrderId(outbox.getOrderId())
                .setRequestId(outbox.getRequestId())
                .setSeckillId(outbox.getSeckillId())
                .setItemId(outbox.getItemId())
                .setUserId(outbox.getUserId())
                .setNum(outbox.getNum())
                .setSeckillPrice(outbox.getSeckillPrice())
                .setTotalFee(outbox.getTotalFee())
                .setCreateTime(outbox.getCreateTime());
    }

    private SeckillRequestMessage toRequestMessage(SeckillOrderOutbox outbox) {
        return new SeckillRequestMessage()
                .setRequestId(outbox.getRequestId())
                .setSeckillId(outbox.getSeckillId())
                .setItemId(outbox.getItemId())
                .setUserId(outbox.getUserId())
                .setNum(outbox.getNum())
                .setSeckillPrice(outbox.getSeckillPrice())
                .setTotalFee(outbox.getTotalFee())
                .setCreateTime(outbox.getCreateTime());
    }

    private int safeRetryCount(SeckillOrderOutbox outbox) {
        return outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
    }

    private int safeMaxRetryCount(SeckillOrderOutbox outbox) {
        return outbox.getMaxRetryCount() == null || outbox.getMaxRetryCount() <= 0
                ? maxRetryCount()
                : outbox.getMaxRetryCount();
    }

    private int maxRetryCount() {
        int configured = properties.getOutbox().getMaxRetryCount();
        return configured > 0 ? configured : DEFAULT_MAX_RETRY_COUNT;
    }

    private int batchSize() {
        int configured = properties.getOutbox().getBatchSize();
        return configured > 0 ? configured : DEFAULT_BATCH_SIZE;
    }

    private long retryDelaySeconds() {
        long configured = properties.getOutbox().getRetryDelaySeconds();
        return configured > 0 ? configured : DEFAULT_RETRY_DELAY_SECONDS;
    }

    private String abbreviate(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
