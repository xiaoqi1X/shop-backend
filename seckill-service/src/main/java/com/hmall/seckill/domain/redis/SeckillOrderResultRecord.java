package com.hmall.seckill.domain.redis;

import lombok.Data;

@Data
public class SeckillOrderResultRecord {

    private Long seckillOrderId;

    private String requestId;

    private Long seckillId;

    private Long itemId;

    private Long userId;

    private String status;

    private String message;

    private Integer totalFee;
}
