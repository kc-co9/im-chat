package com.kim.omgchat.enums;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/16 16:28
 */
public enum MessageTypeEnum {
    /**
     * 登录消息
     */
    LOGIN_MSG(0),
    /**
     * 登出消息
     */
    LOGOUT_MSG(1),
    /**
     * 聊天消息
     */
    CHAT_MSG(2),
    /**
     * 消息数量消息
     */
    COUNT_MSG(3);

    int type;

    MessageTypeEnum(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

}
