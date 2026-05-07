package com.hmall.seckill.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "Seckill order form")
public class SeckillOrderFormDTO {

    @ApiModelProperty("Seckill activity ID")
    private Long seckillId;

    @ApiModelProperty("Item ID")
    private Long itemId;

    @ApiModelProperty("Purchase count")
    private Integer num;
}
