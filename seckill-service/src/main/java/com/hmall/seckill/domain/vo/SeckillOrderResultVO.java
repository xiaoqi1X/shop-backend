package com.hmall.seckill.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "Seckill order result")
public class SeckillOrderResultVO {

    @ApiModelProperty("Seckill order ID")
    private Long seckillOrderId;

    @ApiModelProperty("Seckill request ID")
    private String requestId;

    @ApiModelProperty("Seckill activity ID")
    private Long seckillId;

    @ApiModelProperty("Item ID")
    private Long itemId;

    @ApiModelProperty("Result status")
    private String status;

    @ApiModelProperty("Result message")
    private String message;

    @ApiModelProperty("Total fee in cents")
    private Integer totalFee;
}
