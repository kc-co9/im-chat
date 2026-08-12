package com.co.kc.imchat.service.message.application.lock;

/**
 * IM 消息服务使用的分布式锁场景。
 */
public final class ImMessageLockScene {
    public static final String PRIVATE_CHAT_OPEN = "im:private:chat:open";
    public static final String PRIVATE_MESSAGE_SEND = "im:private:message:send";
    public static final String PRIVATE_MESSAGE_REVOKE = "im:private:message:revoke";
    public static final String GROUP_MESSAGE_SEND = "im:group:message:send";
    public static final String GROUP_MESSAGE_REVOKE = "im:group:message:revoke";

    private ImMessageLockScene() {
    }
}
