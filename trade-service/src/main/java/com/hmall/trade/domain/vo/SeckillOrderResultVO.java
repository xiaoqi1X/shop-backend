package com.hmall.trade.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "秒杀下单结果")
public class SeckillOrderResultVO {

    @ApiModelProperty("秒杀订单ID")
    private Long seckillOrderId;

    @ApiModelProperty("秒杀请求ID，用于异步结果关联")
    private String requestId;

    @ApiModelProperty("秒杀活动ID")
    private Long seckillId;

    @ApiModelProperty("商品ID")
    private Long itemId;

    @ApiModelProperty("结果状态")
    private String status;

    @ApiModelProperty("结果文案")
    private String message;

    @ApiModelProperty("订单总金额，单位分")
    private Integer totalFee;
}
