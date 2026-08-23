package com.co.kc.imchat.service.account.application.lock;

/**
 * IM 账号服务使用的分布式锁场景。
 */
public final class ImAccountLockScene {
    public static final String SESSION_WRITE = "im:account:session:write";

    private ImAccountLockScene() {
    }
}
