package com.hmall.seckill.service;

import com.hmall.seckill.domain.redis.SeckillActivitySnapshot;

public interface SeckillActivityCacheService {

    SeckillActivitySnapshot getActivity(Long seckillId);

    void prewarmActiveActivities();
}
