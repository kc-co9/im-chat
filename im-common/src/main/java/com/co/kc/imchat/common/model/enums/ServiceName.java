package com.co.kc.imchat.common.model.enums;

/**
 * 应用服务在服务发现中的名称。
 */
public enum ServiceName {
    IM_BROKER("im-broker");

    private final String value;

    ServiceName(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
