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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillResultServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private SeckillResultServiceImpl resultService;

    @BeforeEach
    void setUp() {
        SeckillAsyncProperties properties = new SeckillAsyncProperties();
        properties.getRedis().setResultKeyPrefix("seckill:result:");
        properties.getRedis().setResultTtlSeconds(7200);
        resultService = new SeckillResultServiceImpl(stringRedisTemplate, new ObjectMapper(), properties);
        UserContext.setUser(100L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void saveShouldStoreResultWithTtl() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        resultService.save(message(), SeckillStatus.SUCCESS, null, 123L);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("seckill:result:req-1"), payloadCaptor.capture(), eq(Duration.ofSeconds(7200)));
        SeckillOrderResultRecord saved = new ObjectMapper().readValue(payloadCaptor.getValue(), SeckillOrderResultRecord.class);
        assertThat(saved.getSeckillOrderId()).isEqualTo(123L);
        assertThat(saved.getRequestId()).isEqualTo("req-1");
        assertThat(saved.getUserId()).isEqualTo(100L);
        assertThat(saved.getStatus()).isEqualTo(SeckillStatus.SUCCESS.name());
        assertThat(saved.getMessage()).isEqualTo(SeckillStatus.SUCCESS.getMessage());
    }

    @Test
    void queryByRequestIdShouldReturnCurrentUserResult() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("seckill:result:req-1")).thenReturn(payload(100L, SeckillStatus.SUCCESS));

        SeckillOrderResultVO result = resultService.queryByRequestId("req-1");

        assertThat(result.getStatus()).isEqualTo(SeckillStatus.SUCCESS.name());
    }

    @Test
    void queryByRequestIdShouldRejectOtherUserResult() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("seckill:result:req-1")).thenReturn(payload(200L, SeckillStatus.SUCCESS));

        assertThatThrownBy(() -> resultService.queryByRequestId("req-1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void queryByRequestIdShouldReturnAcceptedWhenResultMissing() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("seckill:result:req-1")).thenReturn(null);

        SeckillOrderResultVO result = resultService.queryByRequestId("req-1");

        assertThat(result.getRequestId()).isEqualTo("req-1");
        assertThat(result.getStatus()).isEqualTo(SeckillStatus.ACCEPTED.name());
        assertThat(result.getMessage()).isEqualTo("Request is still being processed");
    }

    @Test
    void queryByRequestIdShouldRejectBlankRequestId() {
        assertThatThrownBy(() -> resultService.queryByRequestId(" "))
                .isInstanceOf(BadRequestException.class);

        verify(stringRedisTemplate, never()).opsForValue();
    }

    private SeckillRequestMessage message() {
        return new SeckillRequestMessage()
                .setRequestId("req-1")
                .setSeckillId(1L)
                .setItemId(317578L)
                .setUserId(100L)
                .setNum(1)
                .setSeckillPrice(9900)
                .setTotalFee(9900)
                .setCreateTime(LocalDateTime.now());
    }

    private String payload(Long userId, SeckillStatus status) throws Exception {
        SeckillOrderResultRecord result = new SeckillOrderResultRecord();
        result.setRequestId("req-1");
        result.setSeckillId(1L);
        result.setItemId(317578L);
        result.setUserId(userId);
        result.setStatus(status.name());
        result.setMessage(status.getMessage());
        result.setTotalFee(9900);
        return new ObjectMapper().writeValueAsString(result);
    }
}
