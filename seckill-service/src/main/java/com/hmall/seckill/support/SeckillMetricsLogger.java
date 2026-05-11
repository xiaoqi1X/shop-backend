package com.hmall.seckill.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public final class SeckillMetricsLogger {

    private static final Logger log = LoggerFactory.getLogger("com.hmall.seckill.metrics.SeckillMetrics");

    private SeckillMetricsLogger() {
    }

    public static long start() {
        return System.nanoTime();
    }

    public static long elapsedMs(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    public static void info(String stage, Object... fields) {
        if (log.isInfoEnabled()) {
            log.info(format(stage, fields));
        }
    }

    public static void warn(String stage, Throwable throwable, Object... fields) {
        if (log.isWarnEnabled()) {
            log.warn(format(stage, fields), throwable);
        }
    }

    private static String format(String stage, Object... fields) {
        StringBuilder builder = new StringBuilder("SECKILL_METRIC stage=").append(stage);
        if (fields == null) {
            return builder.toString();
        }
        for (int i = 0; i + 1 < fields.length; i += 2) {
            builder.append(' ')
                    .append(fields[i])
                    .append('=')
                    .append(fields[i + 1]);
        }
        return builder.toString();
    }
}
