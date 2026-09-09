package com.co.kc.imchat.plugin.lock.support;

/**
 * 分布式锁的过期与等待策略。
 */
public record LockOptions(long expireTimeMs, long waitTimeMs) {
    public static final LockOptions AUTO_RENEW_NO_WAIT =
            new LockOptions(LockConstants.AUTO_RENEW, LockConstants.NEVER_WAIT);

    public static final LockOptions AUTO_RENEW_DEFAULT_WAIT =
            new LockOptions(LockConstants.AUTO_RENEW, LockConstants.DEFAULT_WAIT);

    public static LockOptions autoRenew(long waitTimeMs) {
        return new LockOptions(LockConstants.AUTO_RENEW, waitTimeMs);
    }

    public static LockOptions of(long expireTimeMs, long waitTimeMs) {
        return new LockOptions(expireTimeMs, waitTimeMs);
    }
}
