package com.hmall.seckill.service.impl;

import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.enums.SeckillQuotaResult;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.service.SeckillQuotaService;
import com.hmall.seckill.support.SeckillMetricsLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SeckillQuotaServiceImpl implements SeckillQuotaService {

    private static final Long LUA_SUCCESS = 1L;
    private static final Long LUA_DUPLICATE = 2L;
    private static final Long LUA_SOLD_OUT = 3L;
    private static final Long LUA_NOT_READY = 4L;
    private static final Long LUA_RELEASED = 1L;
    private static final Long LUA_RELEASE_SKIPPED = 2L;
    private static final Long LUA_RELEASE_NOT_READY = 3L;
    private static final long KEY_TTL_HOURS = 48L;
    private static final long KEY_TTL_SECONDS = TimeUnit.HOURS.toSeconds(KEY_TTL_HOURS);

    private static final DefaultRedisScript<Long> QUOTA_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then " +
                    "return 2 " +
                    "end " +
                    "local stock = redis.call('GET', KEYS[1]) " +
                    "if not stock then " +
                    "return 4 " +
                    "end " +
                    "if tonumber(stock) < tonumber(ARGV[2]) then " +
                    "return 3 " +
                    "end " +
                    "redis.call('DECRBY', KEYS[1], ARGV[2]) " +
                    "redis.call('SADD', KEYS[2], ARGV[1]) " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[3]) " +
                    "redis.call('EXPIRE', KEYS[2], ARGV[3]) " +
                    "return 1",
            Long.class
    );

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 0 then " +
                    "return 3 " +
                    "end " +
                    "if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 0 then " +
                    "return 2 " +
                    "end " +
                    "redis.call('INCRBY', KEYS[1], ARGV[2]) " +
                    "redis.call('SREM', KEYS[2], ARGV[1]) " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[3]) " +
                    "redis.call('EXPIRE', KEYS[2], ARGV[3]) " +
                    "return 1",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillAsyncProperties properties;

    @Override
    public SeckillQuotaResult tryAcquire(SeckillRequestMessage requestMessage) {
        long startedAt = SeckillMetricsLogger.start();
        if (requestMessage == null || requestMessage.getSeckillId() == null || requestMessage.getUserId() == null) {
            SeckillMetricsLogger.info("redis_quota", "result", SeckillQuotaResult.FAILED, "reason", "invalid_message", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return SeckillQuotaResult.FAILED;
        }
        int num = requestMessage.getNum() == null ? 1 : requestMessage.getNum();
        if (num <= 0) {
            SeckillMetricsLogger.info("redis_quota", "requestId", requestMessage.getRequestId(), "seckillId", requestMessage.getSeckillId(), "userId", requestMessage.getUserId(), "result", SeckillQuotaResult.FAILED, "reason", "invalid_num", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return SeckillQuotaResult.FAILED;
        }

        String stockKey = stockKey(requestMessage.getSeckillId());
        String usersKey = usersKey(requestMessage.getSeckillId());
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey))) {
            SeckillMetricsLogger.info("redis_quota", "requestId", requestMessage.getRequestId(), "seckillId", requestMessage.getSeckillId(), "userId", requestMessage.getUserId(), "result", SeckillQuotaResult.NOT_READY, "reason", "missing_stock_key", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return SeckillQuotaResult.NOT_READY;
        }

        long luaStartedAt = SeckillMetricsLogger.start();
        Long code = stringRedisTemplate.execute(
                QUOTA_SCRIPT,
                Arrays.asList(stockKey, usersKey),
                requestMessage.getUserId().toString(),
                Integer.toString(num),
                Long.toString(KEY_TTL_SECONDS)
        );
        long luaMs = SeckillMetricsLogger.elapsedMs(luaStartedAt);
        SeckillQuotaResult result;
        if (Objects.equals(code, LUA_SUCCESS)) {
            result = SeckillQuotaResult.SUCCESS;
        } else if (Objects.equals(code, LUA_DUPLICATE)) {
            result = SeckillQuotaResult.DUPLICATE;
        } else if (Objects.equals(code, LUA_SOLD_OUT)) {
            result = SeckillQuotaResult.SOLD_OUT;
        } else if (Objects.equals(code, LUA_NOT_READY)) {
            result = SeckillQuotaResult.NOT_READY;
        } else {
            result = SeckillQuotaResult.FAILED;
        }
        SeckillMetricsLogger.info("redis_quota", "requestId", requestMessage.getRequestId(), "seckillId", requestMessage.getSeckillId(), "userId", requestMessage.getUserId(), "result", result, "luaCode", code, "luaMs", luaMs, "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
        return result;
    }

    @Override
    public void release(SeckillRequestMessage requestMessage) {
        long startedAt = SeckillMetricsLogger.start();
        if (requestMessage == null || requestMessage.getSeckillId() == null || requestMessage.getUserId() == null) {
            SeckillMetricsLogger.info("redis_quota_release", "result", "SKIPPED", "reason", "invalid_message", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return;
        }
        int num = requestMessage.getNum() == null ? 1 : requestMessage.getNum();
        if (num <= 0) {
            SeckillMetricsLogger.info("redis_quota_release", "requestId", requestMessage.getRequestId(), "seckillId", requestMessage.getSeckillId(), "userId", requestMessage.getUserId(), "result", "SKIPPED", "reason", "invalid_num", "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
            return;
        }
        String stockKey = stockKey(requestMessage.getSeckillId());
        String usersKey = usersKey(requestMessage.getSeckillId());
        long luaStartedAt = SeckillMetricsLogger.start();
        Long code = stringRedisTemplate.execute(
                RELEASE_SCRIPT,
                Arrays.asList(stockKey, usersKey),
                requestMessage.getUserId().toString(),
                Integer.toString(num),
                Long.toString(KEY_TTL_SECONDS)
        );
        long luaMs = SeckillMetricsLogger.elapsedMs(luaStartedAt);
        String result;
        if (Objects.equals(code, LUA_RELEASED)) {
            result = "RELEASED";
        } else if (Objects.equals(code, LUA_RELEASE_SKIPPED)) {
            result = "SKIPPED";
        } else if (Objects.equals(code, LUA_RELEASE_NOT_READY)) {
            result = "NOT_READY";
        } else {
            result = "FAILED";
        }
        SeckillMetricsLogger.info("redis_quota_release", "requestId", requestMessage.getRequestId(), "seckillId", requestMessage.getSeckillId(), "userId", requestMessage.getUserId(), "result", result, "luaCode", code, "luaMs", luaMs, "totalMs", SeckillMetricsLogger.elapsedMs(startedAt));
    }

    private String stockKey(Long seckillId) {
        return properties.getRedis().getStockKeyPrefix() + seckillId;
    }

    private String usersKey(Long seckillId) {
        return properties.getRedis().getUsersKeyPrefix() + seckillId;
    }
}
