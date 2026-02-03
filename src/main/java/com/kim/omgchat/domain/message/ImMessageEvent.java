package com.kim.omgchat.domain.message;

public enum ImMessageEvent {
    /**
     * 已发送
     */
    SEND,

    /**
     * 已读
     */
    READ,

    /**
     * 已撤回
     */
    REVOKE;
}
