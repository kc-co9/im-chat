package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * Gateway 内部通信主机，供 Broker 定向写帧及复核已注册端点。
 */
public record GatewayHost(String value) {
    public GatewayHost {
        AssertUtils.domainPropNotBlank("Gateway host is required", value);
    }
}
