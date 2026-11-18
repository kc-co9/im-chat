package com.co.kc.imchat.plugin.raft.spi;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 原生角色事件的短回调；实现不得等待、调用 Node 或发布应用 continuation。
 */
public interface RaftRoleListener {
    /**
     * 启动前与 runtime 共享的终身故障门；所有准入必须检查同一实例。
     */
    FaultLatch faultLatch();

    /**
     * 任期与 generation 已由 runtime 发布；停止事件的 term 为 0。
     */
    void onRoleChanged(long term, long generation);

    /**
     * 首因即永久故障，不能清除或覆盖；不执行回调，不获取外部锁。
     */
    final class FaultLatch {
        /**
         * 只保留最先发生的 runtime 故障。
         */
        private final AtomicReference<RuntimeException> firstFailure = new AtomicReference<>();

        /**
         * 只记录首个故障，后续故障不能覆盖最初原因。
         */
        public void fail(RuntimeException failure) {
            firstFailure.compareAndSet(null, Objects.requireNonNull(failure));
        }

        /**
         * 判断 runtime 是否已经进入永久故障状态。
         */
        public boolean faulted() {
            return firstFailure.get() != null;
        }

        /**
         * 返回首因，供启动和准入边界记录或解释故障。
         */
        public Optional<RuntimeException> failure() {
            return Optional.ofNullable(firstFailure.get());
        }
    }
}
