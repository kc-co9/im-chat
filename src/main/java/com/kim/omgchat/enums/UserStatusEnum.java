package com.kim.omgchat.enums;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/16 16:37
 */
public enum UserStatusEnum {
    /**
     * 在线
     */
    ONLINE("online"),
    /**
     * 离线
     */
    OFFLINE("offline");

    String status;

    UserStatusEnum(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

}
