package com.kim.omgchat.holder;

import lombok.Data;

import javax.websocket.Session;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/16 15:47
 */
@Data
public class WsChatSession {
    /**
     * 聊天Session
     **/
    private Session session;

    /**
     * 用户锁定用户Session
     **/
    private Long toChatUserId;
}
