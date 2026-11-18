package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Duration;

/**
 * 本机单调时钟时间戳。
 *
 * <p>该值对象封装 {@link System#nanoTime()} 语义下的时间点，只能用于同一进程内的耗时、
 * 超时和 Lease 截止时间计算。它不代表墙上时间，不能跨进程持久化或作为协议字段传递。</p>
 */
public record MonotonicTimestamp(
        /* 单调时钟纳秒值。 */
        Long nanos) {

    private static final long ORIGIN_NANOS = System.nanoTime();

    public MonotonicTimestamp {
        AssertUtils.argNotNull("monotonic timestamp is required", nanos);
        AssertUtils.argTrue("monotonic timestamp must not be negative", nanos >= 0L);
    }

    public static MonotonicTimestamp now() {
        return new MonotonicTimestamp(Math.max(0L, System.nanoTime() - ORIGIN_NANOS));
    }

    public MonotonicTimestamp plus(Duration duration) {
        AssertUtils.argNotNull("duration must not be null", duration);
        return new MonotonicTimestamp(Math.addExact(nanos, duration.toNanos()));
    }

    public boolean hasReached(MonotonicTimestamp deadline) {
        AssertUtils.argNotNull("deadline must not be null", deadline);
        return nanos >= deadline.nanos;
    }
}
