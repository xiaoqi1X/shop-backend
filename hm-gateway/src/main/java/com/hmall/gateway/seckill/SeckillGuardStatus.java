package com.hmall.gateway.seckill;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeckillGuardStatus {
    DISABLED("Activity disabled"),
    NOT_STARTED("Activity not started"),
    ENDED("Activity ended"),
    NOT_READY("Seckill not ready"),
    INVALID_ACTIVITY("Invalid activity"),
    LIMIT_EXCEEDED("Limit exceeded"),
    FAILED("Seckill failed");

    private final String message;
}
