package com.hmall.seckill.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("seckill_activity")
public class SeckillActivity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long itemId;

    private Integer seckillPrice;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer limitCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
