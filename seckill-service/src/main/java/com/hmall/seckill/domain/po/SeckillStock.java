package com.hmall.seckill.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("seckill_stock")
public class SeckillStock {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long seckillId;

    private Integer totalStock;

    private Integer availableStock;

    private Integer soldCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
