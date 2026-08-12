package com.co.kc.imchat.plugin.lock.spi;

/**
 * 获取锁后执行的回调。
 *
 * @param <T> 回调返回类型
 */
@FunctionalInterface
public interface LockCallback<T> {
    /**
     * 执行业务逻辑。
     *
     * @return 执行结果
     * @throws Throwable 业务异常
     */
    T get() throws Throwable;
}
