package com.hmall.seckill.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeckillStatus {
    DISABLED("Activity disabled"),
    NOT_STARTED("Activity not started"),
    IN_PROGRESS("In progress"),
    ENDED("Activity ended"),
    SOLD_OUT("Sold out"),
    NOT_READY("Seckill not ready"),
    ACCEPTED("Request accepted"),
    QUOTA_SUCCESS("Quota acquired, order is being finalized"),
    SUCCESS("Seckill success"),
    FINALIZED("Order finalized"),
    DUPLICATE_ORDER("Duplicate order"),
    INVALID_ACTIVITY("Invalid activity"),
    LIMIT_EXCEEDED("Limit exceeded"),
    FAILED("Seckill failed");

    private final String message;
}
