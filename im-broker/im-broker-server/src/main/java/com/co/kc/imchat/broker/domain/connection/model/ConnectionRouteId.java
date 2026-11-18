package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.domain.user.model.UserId;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * GatewayRouteTable 内连接路由实体的组合身份值对象。
 *
 * <p>身份由用户和 Gateway 共同组成，因为同一用户可以在不同 Gateway 上分别存在路由。
 * 该值对象用于增删投影、快照对账和旧路由清理，不能只用 UserId 替代。</p>
 */
public record ConnectionRouteId(UserId userId, GatewayId gatewayId) {

    public ConnectionRouteId {
        AssertUtils.allDomainPropNotNull("connection route id is incomplete", userId, gatewayId);
    }
}
