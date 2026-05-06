package com.hmall.trade.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "秒杀商品展示信息")
public class SeckillItemVO {

    @ApiModelProperty("秒杀活动ID")
    private Long seckillId;

    @ApiModelProperty("商品ID")
    private Long itemId;

    @ApiModelProperty("商品名称")
    private String name;

    @ApiModelProperty("商品图片")
    private String image;

    @ApiModelProperty("原价，单位分")
    private Integer originalPrice;

    @ApiModelProperty("秒杀价，单位分")
    private Integer seckillPrice;

    @ApiModelProperty("活动开始时间")
    private LocalDateTime startTime;

    @ApiModelProperty("活动结束时间")
    private LocalDateTime endTime;

    @ApiModelProperty("限购数量")
    private Integer limitCount;

    @ApiModelProperty("剩余库存")
    private Integer availableStock;

    @ApiModelProperty("已售数量")
    private Integer soldCount;

    @ApiModelProperty("状态码")
    private String status;

    @ApiModelProperty("状态文案")
    private String statusText;
}
