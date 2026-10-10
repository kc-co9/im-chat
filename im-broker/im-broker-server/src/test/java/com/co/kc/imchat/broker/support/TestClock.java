package com.co.kc.imchat.broker.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.LongSupplier;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** 将测试中的固定或可变 epoch millis 映射为标准 Clock。 */
public final class TestClock extends Clock {

    private final LongSupplier epochMillis;

    private TestClock(LongSupplier epochMillis) {
        this.epochMillis = epochMillis;
    }

    public static Clock fixed(long epochMillis) {
        return Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    public static Clock from(LongSupplier epochMillis) {
        return new TestClock(epochMillis);
    }

    /** 仅在当前测试线程控制墙上时间，调用方必须在测试结束时关闭作用域。 */
    public static MockedStatic<Instant> interceptNow(LongSupplier epochMillis) {
        MockedStatic<Instant> scope = mockStatic(Instant.class, CALLS_REAL_METHODS);
        scope.when(Instant::now).thenAnswer(ignored -> Instant.ofEpochMilli(epochMillis.getAsLong()));
        return scope;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (ZoneOffset.UTC.equals(zone)) {
            return this;
        }
        return Clock.fixed(instant(), zone);
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis());
    }

    @Override
    public long millis() {
        return epochMillis.getAsLong();
    }
}
