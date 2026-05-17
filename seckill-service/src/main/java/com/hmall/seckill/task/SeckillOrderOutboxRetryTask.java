package com.hmall.seckill.task;

import com.hmall.seckill.service.SeckillOrderOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "seckill.async.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeckillOrderOutboxRetryTask {

    private final SeckillOrderOutboxService outboxService;

    @Scheduled(fixedDelayString = "${seckill.async.outbox.scan-interval-ms:5000}")
    public void retryDueMessages() {
        outboxService.retryDueMessages();
    }
}
