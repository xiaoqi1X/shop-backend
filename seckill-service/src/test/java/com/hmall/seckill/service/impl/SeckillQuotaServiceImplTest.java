package com.hmall.seckill.service.impl;

import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillQuotaServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private SeckillQuotaServiceImpl quotaService;

    @BeforeEach
    void setUp() {
        SeckillAsyncProperties properties = new SeckillAsyncProperties();
        properties.getRedis().setStockKeyPrefix("seckill:stock:");
        properties.getRedis().setUsersKeyPrefix("seckill:users:");
        quotaService = new SeckillQuotaServiceImpl(stringRedisTemplate, properties);
    }

    @Test
    void releaseShouldUseLuaScriptAtomically() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), eq("100"), eq("1"), eq("172800")))
                .thenReturn(1L);

        quotaService.release(message(1));

        verify(stringRedisTemplate).execute(any(RedisScript.class), eq(Arrays.asList("seckill:stock:1", "seckill:users:1")), eq("100"), eq("1"), eq("172800"));
    }

    @Test
    void releaseShouldSkipInvalidMessage() {
        quotaService.release(new SeckillRequestMessage().setSeckillId(1L).setUserId(100L).setNum(0));

        verify(stringRedisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
    }

    private SeckillRequestMessage message(Integer num) {
        return new SeckillRequestMessage()
                .setRequestId("req-1")
                .setSeckillId(1L)
                .setUserId(100L)
                .setNum(num);
    }
}
