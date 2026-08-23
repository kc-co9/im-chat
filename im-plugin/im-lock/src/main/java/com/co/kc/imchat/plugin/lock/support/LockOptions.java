package com.co.kc.imchat.plugin.lock.support;

/**
 * 分布式锁的过期与等待策略。
 */
public record LockOptions(long expireTimeMs, long waitTimeMs) {
    private static final long DEFAULT_WAIT_TIME_MS = 100L;

    public static final LockOptions AUTO_RENEW_NO_WAIT =
            new LockOptions(LockConstants.AUTO_RENEW, LockConstants.NEVER_WAIT);

    public static final LockOptions AUTO_RENEW_DEFAULT_WAIT =
            new LockOptions(LockConstants.AUTO_RENEW, DEFAULT_WAIT_TIME_MS);

    public static LockOptions autoRenew(long waitTimeMs) {
        return new LockOptions(LockConstants.AUTO_RENEW, waitTimeMs);
    }

    public static LockOptions of(long expireTimeMs, long waitTimeMs) {
        return new LockOptions(expireTimeMs, waitTimeMs);
    }
}
