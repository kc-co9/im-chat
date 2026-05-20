package com.co.kc.imchat.domain.message.event;

public enum ImMessageEvent {
    /**
     * 已发送
     */
    SEND,

    /**
     * 接收
     */
    RECEIVE,

    /**
     * 已读
     */
    READ,

    /**
     * 已撤回
     */
    REVOKE;
}
