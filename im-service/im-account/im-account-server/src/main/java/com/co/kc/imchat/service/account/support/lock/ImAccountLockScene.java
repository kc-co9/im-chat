package com.co.kc.imchat.service.account.support.lock;

/**
 * IM 账号服务使用的分布式锁场景。
 */
public final class ImAccountLockScene {
    public static final String SESSION_WRITE = "im:account:session:write";
    public static final String USER_WRITE = "im:account:user:write";
    public static final String USER_ADMIN_WRITE = "im:account:user:admin:write";

    private ImAccountLockScene() {
    }
}
