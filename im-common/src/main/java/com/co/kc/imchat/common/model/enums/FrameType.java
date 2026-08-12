package com.co.kc.imchat.common.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 实时协议下行帧类型。
 */
public enum FrameType {
    /**
     * 普通请求响应帧。
     */
    RESPONSE("response"),

    /**
     * 服务端主动推送帧。
     */
    PUSH("push"),

    /**
     * 协议错误帧。
     */
    ERROR("error");

    private final String value;

    FrameType(String value) {
        this.value = value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static FrameType of(String value) {
        for (FrameType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown frame type: " + value);
    }

    @JsonValue
    public String value() {
        return value;
    }
}
