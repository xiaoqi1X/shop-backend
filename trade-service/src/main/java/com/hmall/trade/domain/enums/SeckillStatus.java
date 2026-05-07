package com.hmall.trade.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeckillStatus {
    DISABLED("活动未启用"),
    NOT_STARTED("活动未开始"),
    IN_PROGRESS("抢购中"),
    ENDED("活动已结束"),
    SOLD_OUT("已售罄"),
    ACCEPTED("请求已受理，正在排队处理"),
    SUCCESS("抢购成功"),
    FINALIZED("订单已确认"),
    DUPLICATE_ORDER("您已抢购过该活动"),
    INVALID_ACTIVITY("秒杀活动无效"),
    LIMIT_EXCEEDED("超过限购数量"),
    FAILED("抢购失败");

    private final String message;
}
