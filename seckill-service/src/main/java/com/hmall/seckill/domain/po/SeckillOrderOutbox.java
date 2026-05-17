package com.hmall.seckill.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("seckill_order_outbox")
public class SeckillOrderOutbox {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String requestId;

    private Long orderId;

    private Long seckillId;

    private Long itemId;

    private Long userId;

    private Integer num;

    private Integer seckillPrice;

    private Integer totalFee;

    private String status;

    private Integer retryCount;

    private Integer maxRetryCount;

    private LocalDateTime nextRetryTime;

    private String lastError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
