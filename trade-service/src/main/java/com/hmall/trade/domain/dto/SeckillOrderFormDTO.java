package com.hmall.trade.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "秒杀下单表单")
public class SeckillOrderFormDTO {

    @ApiModelProperty("秒杀活动ID")
    private Long seckillId;

    @ApiModelProperty("商品ID")
    private Long itemId;

    @ApiModelProperty("购买数量")
    private Integer num;
}
