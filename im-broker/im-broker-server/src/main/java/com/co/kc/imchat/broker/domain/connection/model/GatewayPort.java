package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * Gateway 内部通信端口，取值为 1 到 65535。
 */
public record GatewayPort(Integer value) {
    public GatewayPort {
        AssertUtils.domainPropNotNull("Gateway port is required", value);
        AssertUtils.domainPropTrue("Gateway port is outside the valid range", value >= 1 && value <= 65535);
    }
}
