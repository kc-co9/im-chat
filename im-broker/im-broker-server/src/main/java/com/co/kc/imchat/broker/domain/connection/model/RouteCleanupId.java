package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 某个集群操作对指定 Gateway 路由执行清理的领域身份，用于识别重复清理。
 */
public record RouteCleanupId(
        /* 已登记排空操作。 */ ClusterOperationId operationId,
        /* 本次清理对应的 Gateway。 */ GatewayId gatewayId) {
    public RouteCleanupId {
        AssertUtils.allDomainPropNotNull("route cleanup identity is incomplete", operationId, gatewayId);
    }
}
