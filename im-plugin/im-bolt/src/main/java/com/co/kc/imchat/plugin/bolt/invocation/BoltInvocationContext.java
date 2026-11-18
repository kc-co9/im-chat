package com.co.kc.imchat.plugin.bolt.invocation;

import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 单次调用的本机目标与单调时间预算，不进入 wire 或共享 Url。
 * <p>
 * 预算覆盖该 caller 的等待和发送，不延长共享连接创建预算，也不授权取消共享创建任务。
 */
public final class BoltInvocationContext {
    /** 原生 InvokeContext 中存取本次调用上下文的键。 */
    public static final String KEY = BoltInvocationContext.class.getName();
    /** 本次调用要求绑定的地址与对端身份。 */
    private final BoltTarget target;
    /** 单调时钟记录的调用起点，单位为纳秒。 */
    private final long startNanos;
    /** 本次调用的总超时时长，单位为纳秒。 */
    private final long timeoutNanos;

    /** 保存目标、单调时间起点和毫秒预算；目标为空或预算非正时拒绝构造。 */
    BoltInvocationContext(BoltTarget target, long startNanos, int timeoutMillis) {
        /* 1. 要求目标存在且 caller 预算为正，拒绝无效调用上下文。 */
        if (target == null || timeoutMillis <= 0) {
            throw new IllegalArgumentException("Bolt target and positive timeout required");
        }
        /* 2. 固定目标与单调时间起点，将毫秒预算统一换算为纳秒。 */
        this.target = target;
        this.startNanos = startNanos;
        this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }

    /** 返回本次调用绑定的目标。 */
    public BoltTarget target() {
        return target;
    }

    /** 按传入的同源单调纳秒时间判断总预算是否耗尽。 */
    public boolean expired(long now) {
        return now - startNanos >= timeoutNanos;
    }

    /** 返回向下取整且不超过 int 上限的剩余毫秒；不足一毫秒时抛出 LOCAL 调用异常。 */
    int remainingMillis(long now) {
        /* 1. 按同源单调时间扣除已用预算，不重置起点或延长本次调用。 */
        long remaining = timeoutNanos - (now - startNanos);
        /* 2. 不足一毫秒即按本地预算耗尽拒绝，避免向原生 API 传入零超时。 */
        if (remaining < TimeUnit.MILLISECONDS.toNanos(1)) {
            throw new BoltInvocationException(BoltInvocationException.Failure.LOCAL, "Bolt caller budget exhausted", new TimeoutException("caller budget"));
        }
        /* 3. 剩余预算向下取整为毫秒，并限制在原生 int 超时范围内。 */
        return (int) Math.min(Integer.MAX_VALUE, TimeUnit.NANOSECONDS.toMillis(remaining));
    }
}
