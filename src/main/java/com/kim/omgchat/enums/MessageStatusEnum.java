package com.kim.omgchat.enums;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/16 16:11
 */
public enum MessageStatusEnum {
    /**
     * 已读
     */
    READ(1),
    /**
     * 未读
     */
    NOT_READ(0);

    int value;

    MessageStatusEnum(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
