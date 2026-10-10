package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 注册 Gateway 绑定的认证主体标识，用于阻止其他主体复用相同进程标识。
 * GatewayId 标识进程，本值标识注册该进程的受信主体，两者不可互换。
 * 本值只保存已经由入口认证的身份，不执行证书认证或推断身份格式。
 */
public record GatewayPrincipalId(String value) {
    public GatewayPrincipalId {
        AssertUtils.domainPropNotBlank("Gateway workload identity is required", value);
    }
}
