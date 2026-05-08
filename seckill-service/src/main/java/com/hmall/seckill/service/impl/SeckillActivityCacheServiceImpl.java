package com.hmall.seckill.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.seckill.config.SeckillAsyncProperties;
import com.hmall.seckill.domain.po.SeckillActivity;
import com.hmall.seckill.domain.po.SeckillStock;
import com.hmall.seckill.domain.redis.SeckillActivitySnapshot;
import com.hmall.seckill.mapper.SeckillActivityMapper;
import com.hmall.seckill.mapper.SeckillStockMapper;
import com.hmall.seckill.service.SeckillActivityCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillActivityCacheServiceImpl implements SeckillActivityCacheService, InitializingBean {

    private static final int ACTIVITY_ENABLED = 1;
    private static final Duration AFTER_END_TTL = Duration.ofHours(48);
    private static final Duration MIN_TTL = Duration.ofHours(1);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SeckillAsyncProperties properties;
    private final SeckillActivityMapper activityMapper;
    private final SeckillStockMapper stockMapper;

    @Override
    public void afterPropertiesSet() {
        prewarmActiveActivities();
    }

    @Override
    public SeckillActivitySnapshot getActivity(Long seckillId) {
        if (seckillId == null) {
            return null;
        }
        String value = stringRedisTemplate.opsForValue().get(activityKey(seckillId));
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, SeckillActivitySnapshot.class);
        } catch (Exception e) {
            log.warn("Failed to parse seckill activity snapshot, seckillId={}", seckillId, e);
            return null;
        }
    }

    @Override
    public void prewarmActiveActivities() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillActivity> activities = activityMapper.selectList(
                Wrappers.<SeckillActivity>lambdaQuery()
                        .eq(SeckillActivity::getStatus, ACTIVITY_ENABLED)
                        .gt(SeckillActivity::getEndTime, now)
        );
        if (activities == null || activities.isEmpty()) {
            log.info("No active seckill activities to prewarm");
            return;
        }
        int activityCount = 0;
        int stockCount = 0;
        for (SeckillActivity activity : activities) {
            Duration ttl = ttlUntilAfterEnd(activity.getEndTime(), now);
            if (writeActivity(activity, ttl)) {
                activityCount++;
            }
            if (writeStockIfAbsent(activity.getId(), ttl)) {
                stockCount++;
            }
            stringRedisTemplate.expire(usersKey(activity.getId()), ttl.getSeconds(), TimeUnit.SECONDS);
        }
        log.info("Prewarmed seckill Redis cache, activities={}, initializedStocks={}, scanned={}",
                activityCount, stockCount, activities.size());
    }

    private boolean writeActivity(SeckillActivity activity, Duration ttl) {
        SeckillActivitySnapshot snapshot = new SeckillActivitySnapshot()
                .setSeckillId(activity.getId())
                .setItemId(activity.getItemId())
                .setSeckillPrice(activity.getSeckillPrice())
                .setStartTime(activity.getStartTime())
                .setEndTime(activity.getEndTime())
                .setLimitCount(activity.getLimitCount())
                .setStatus(activity.getStatus());
        try {
            stringRedisTemplate.opsForValue().set(
                    activityKey(activity.getId()),
                    objectMapper.writeValueAsString(snapshot),
                    ttl.getSeconds(),
                    TimeUnit.SECONDS
            );
            return true;
        } catch (Exception e) {
            log.warn("Failed to write seckill activity snapshot, seckillId={}", activity.getId(), e);
            return false;
        }
    }

    private boolean writeStockIfAbsent(Long seckillId, Duration ttl) {
        SeckillStock stock = stockMapper.selectOne(
                Wrappers.<SeckillStock>lambdaQuery().eq(SeckillStock::getSeckillId, seckillId)
        );
        if (stock == null || stock.getAvailableStock() == null) {
            log.warn("Skip seckill stock prewarm because stock row is missing, seckillId={}", seckillId);
            return false;
        }
        Boolean initialized = stringRedisTemplate.opsForValue().setIfAbsent(
                stockKey(seckillId),
                stock.getAvailableStock().toString(),
                ttl.getSeconds(),
                TimeUnit.SECONDS
        );
        if (Boolean.TRUE.equals(initialized)) {
            return true;
        }
        stringRedisTemplate.expire(stockKey(seckillId), ttl.getSeconds(), TimeUnit.SECONDS);
        return false;
    }

    private Duration ttlUntilAfterEnd(LocalDateTime endTime, LocalDateTime now) {
        if (endTime == null || !endTime.isAfter(now)) {
            return MIN_TTL;
        }
        Duration ttl = Duration.between(now, endTime).plus(AFTER_END_TTL);
        return ttl.compareTo(MIN_TTL) < 0 ? MIN_TTL : ttl;
    }

    private String activityKey(Long seckillId) {
        return properties.getRedis().getActivityKeyPrefix() + seckillId;
    }

    private String stockKey(Long seckillId) {
        return properties.getRedis().getStockKeyPrefix() + seckillId;
    }

    private String usersKey(Long seckillId) {
        return properties.getRedis().getUsersKeyPrefix() + seckillId;
    }
}
