package com.co.kc.imchat.plugin.lock.support;

/**
 * 分布式锁 key 构造工具。
 */
public final class LockKeys {

    private LockKeys() {
    }

    public static String userPair(Long leftUserId, Long rightUserId) {
        if (leftUserId == null || rightUserId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        long min = Math.min(leftUserId, rightUserId);
        long max = Math.max(leftUserId, rightUserId);
        return min + ":" + max;
    }
}
