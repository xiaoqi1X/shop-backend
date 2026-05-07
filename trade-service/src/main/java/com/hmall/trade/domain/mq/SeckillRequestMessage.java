package com.hmall.trade.domain.mq;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class SeckillRequestMessage {

    private String requestId;

    private Long seckillId;

    private Long itemId;

    private Long userId;

    private Integer num;

    private Integer seckillPrice;

    private Integer totalFee;

    private LocalDateTime createTime;
}
