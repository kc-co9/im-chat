package com.co.kc.imchat.plugin.lock.core;

import java.io.Serial;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 本机非重入、非公平锁；一个实例对应一个互斥域，所有权不绑定线程。
 *
 * <p>允许获取线程将释放责任交给异步完成线程。同一线程再次获取也视为竞争，
 * 不累计重入次数；不支持 Condition，不自动过期，也不提供跨进程互斥。</p>
 * <p>调用方必须共享同一实例，并保证每次成功获取只在任务实际结束后释放一次。
 * 空闲时释放会抛出 IllegalMonitorStateException；占用期间无法识别非所属释放，
 * 也无法识别旧任务的重复释放是否误释放了后续任务，不能将此锁视为持有者认证。</p>
 * <p>成功释放前的写入对随后成功获取同一锁的线程可见。
 * 跨线程交接任务和释放责任时，调用方仍须建立正常的 happens-before 关系。</p>
 */
public final class MutexLock implements Lock {

    private final Sync sync = new Sync();

    @Override
    public void lock() {
        sync.acquire(1);
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    @Override
    public boolean tryLock() {
        return sync.tryAcquire(1);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, Objects.requireNonNull(unit, "unit").toNanos(time));
    }

    @Override
    public void unlock() {
        sync.release(1);
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public Condition newCondition() {
        throw new UnsupportedOperationException("MutexLock does not support conditions");
    }

    private static final class Sync extends AbstractQueuedSynchronizer {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean tryAcquire(int ignored) {
            return compareAndSetState(0, 1);
        }

        @Override
        protected boolean tryRelease(int ignored) {
            if (!compareAndSetState(1, 0)) {
                throw new IllegalMonitorStateException("MutexLock is not held");
            }
            return true;
        }
    }
}
