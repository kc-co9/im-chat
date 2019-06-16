package com.kim.omgchat.holder;

import lombok.Data;

import javax.websocket.Session;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/16 13:37
 */
@Data
public class WsSession {
    /**
     * 主页Session
     **/
    private Session indexSession;

    /**
     * 聊天Session
     **/
    private Session chatSession;

    /**
     * 用户锁定用户Session
     **/
    private Long toChatUserId;
}
