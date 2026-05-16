package com.co.kc.imchat.support.lock;

public class LockKeys {

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
