package com.hmall.seckill.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "Seckill item view")
public class SeckillItemVO {

    @ApiModelProperty("Seckill activity ID")
    private Long seckillId;

    @ApiModelProperty("Item ID")
    private Long itemId;

    @ApiModelProperty("Item name")
    private String name;

    @ApiModelProperty("Item image")
    private String image;

    @ApiModelProperty("Original price in cents")
    private Integer originalPrice;

    @ApiModelProperty("Seckill price in cents")
    private Integer seckillPrice;

    @ApiModelProperty("Start time")
    private LocalDateTime startTime;

    @ApiModelProperty("End time")
    private LocalDateTime endTime;

    @ApiModelProperty("Limit count")
    private Integer limitCount;

    @ApiModelProperty("Available stock")
    private Integer availableStock;

    @ApiModelProperty("Sold count")
    private Integer soldCount;

    @ApiModelProperty("Status code")
    private String status;

    @ApiModelProperty("Status text")
    private String statusText;
}
