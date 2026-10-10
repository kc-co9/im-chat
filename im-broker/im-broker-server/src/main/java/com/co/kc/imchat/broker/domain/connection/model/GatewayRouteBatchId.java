package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * Gateway 一轮完整路由上报的批次标识，与 Gateway 身份共同确定接收批次。
 */
public record GatewayRouteBatchId(
        /* 批次所属 Gateway，不同进程的同名批次互不影响。 */ GatewayId gatewayId,
        /* Gateway 本轮上报的标识。 */ String value) {
    public GatewayRouteBatchId {
        AssertUtils.domainPropNotNull("Gateway route batch owner is required", gatewayId);
        AssertUtils.domainPropNotBlank("Gateway route batch id must not be blank", value);
    }
}
