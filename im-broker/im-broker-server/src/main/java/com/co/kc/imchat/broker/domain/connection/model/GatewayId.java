package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 标识一个 Gateway 进程实例的值对象。
 *
 * <p>该标识是 GatewayRouteTable 聚合的身份，也是路由快照、注册水位和连接路由的归属边界。
 * Gateway 重启后必须使用新的标识，避免新旧进程的路由事件在同一聚合内混淆。</p>
 */
public record GatewayId(String value) {

    public GatewayId {
        AssertUtils.domainPropNotBlank("gatewayId must not be blank", value);
    }
}
