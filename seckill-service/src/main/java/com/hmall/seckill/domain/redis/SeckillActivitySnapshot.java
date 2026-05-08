package com.hmall.seckill.domain.redis;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class SeckillActivitySnapshot {

    private Long seckillId;

    private Long itemId;

    private Integer seckillPrice;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer limitCount;

    private Integer status;
}
