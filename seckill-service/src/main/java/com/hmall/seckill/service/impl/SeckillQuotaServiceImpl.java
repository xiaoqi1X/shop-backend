package com.hmall.seckill.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.enums.SeckillQuotaResult;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.domain.po.SeckillStock;
import com.hmall.seckill.mapper.SeckillStockMapper;
import com.hmall.seckill.service.SeckillQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SeckillQuotaServiceImpl implements SeckillQuotaService {

    private static final Long LUA_SUCCESS = 1L;
    private static final Long LUA_DUPLICATE = 2L;
    private static final Long LUA_SOLD_OUT = 3L;
    private static final Long LUA_NOT_READY = 4L;
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

    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillAsyncProperties properties;
    private final SeckillStockMapper stockMapper;

    @Override
    public SeckillQuotaResult tryAcquire(SeckillRequestMessage requestMessage) {
        if (requestMessage == null || requestMessage.getSeckillId() == null || requestMessage.getUserId() == null) {
            return SeckillQuotaResult.FAILED;
        }
        int num = requestMessage.getNum() == null ? 1 : requestMessage.getNum();
        if (num <= 0) {
            return SeckillQuotaResult.FAILED;
        }

        String stockKey = stockKey(requestMessage.getSeckillId());
        String usersKey = usersKey(requestMessage.getSeckillId());
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey)) && !loadStock(stockKey, usersKey, requestMessage.getSeckillId())) {
            return SeckillQuotaResult.NOT_READY;
        }

        Long code = stringRedisTemplate.execute(
                QUOTA_SCRIPT,
                Arrays.asList(stockKey, usersKey),
                requestMessage.getUserId().toString(),
                Integer.toString(num),
                Long.toString(KEY_TTL_SECONDS)
        );
        if (Objects.equals(code, LUA_SUCCESS)) {
            return SeckillQuotaResult.SUCCESS;
        }
        if (Objects.equals(code, LUA_DUPLICATE)) {
            return SeckillQuotaResult.DUPLICATE;
        }
        if (Objects.equals(code, LUA_SOLD_OUT)) {
            return SeckillQuotaResult.SOLD_OUT;
        }
        if (Objects.equals(code, LUA_NOT_READY)) {
            return SeckillQuotaResult.NOT_READY;
        }
        return SeckillQuotaResult.FAILED;
    }

    @Override
    public void release(SeckillRequestMessage requestMessage) {
        if (requestMessage == null || requestMessage.getSeckillId() == null || requestMessage.getUserId() == null) {
            return;
        }
        int num = requestMessage.getNum() == null ? 1 : requestMessage.getNum();
        if (num <= 0) {
            return;
        }
        String stockKey = stockKey(requestMessage.getSeckillId());
        String usersKey = usersKey(requestMessage.getSeckillId());
        stringRedisTemplate.opsForValue().increment(stockKey, num);
        stringRedisTemplate.opsForSet().remove(usersKey, requestMessage.getUserId().toString());
    }

    private boolean loadStock(String stockKey, String usersKey, Long seckillId) {
        SeckillStock stock = stockMapper.selectOne(
                Wrappers.<SeckillStock>lambdaQuery().eq(SeckillStock::getSeckillId, seckillId)
        );
        if (stock == null || stock.getAvailableStock() == null) {
            return false;
        }
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(stockKey, stock.getAvailableStock().toString(), KEY_TTL_HOURS, TimeUnit.HOURS);
        stringRedisTemplate.expire(usersKey, KEY_TTL_HOURS, TimeUnit.HOURS);
        return Boolean.TRUE.equals(success) || Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey));
    }

    private String stockKey(Long seckillId) {
        return properties.getRedis().getStockKeyPrefix() + seckillId;
    }

    private String usersKey(Long seckillId) {
        return properties.getRedis().getUsersKeyPrefix() + seckillId;
    }
}
