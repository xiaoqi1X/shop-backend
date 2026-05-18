package com.hmall.gateway.seckill;

import lombok.Data;

@Data
public class SeckillGuardResult {

    private Long seckillOrderId;

    private String requestId;

    private Long seckillId;

    private Long itemId;

    private String status;

    private String message;

    private Integer totalFee;

    public static SeckillGuardResult of(Long seckillId, Long itemId, String status, String message) {
        SeckillGuardResult result = new SeckillGuardResult();
        result.setSeckillId(seckillId);
        result.setItemId(itemId);
        result.setStatus(status);
        result.setMessage(message);
        return result;
    }
}
