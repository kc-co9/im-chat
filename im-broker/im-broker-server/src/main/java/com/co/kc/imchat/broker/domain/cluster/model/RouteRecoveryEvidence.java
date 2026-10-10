package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * 排空或故障恢复完成前的路由重建与旧路由清理证据。
 *
 * <p>汇总同时记录新 RouteBroker 已重建的数量、旧 RouteBroker 已删除的数量、仍残留的
 * 旧路由数量和内容摘要是否匹配。BrokerCluster 只有在摘要匹配、重建数量与 Gateway
 * 确认数量一致且残留数量为零时，才允许正常完成 drain。</p>
 */
public record RouteRecoveryEvidence(
        /* 在新 RouteBroker 集合中成功重建的路由数量。 */
        RouteCount rebuiltRouteCount,
        /* 从旧 RouteBroker 删除的路由数量。 */
        RouteCount removedOldRouteCount,
        /* 验证后仍残留在旧 RouteBroker 的路由数量。 */
        RouteCount residualOldRouteCount,
        /* Gateway 确认与 RouteBroker 重建摘要是否一致。 */
        Boolean checksumsMatch) implements Serializable {

    public RouteRecoveryEvidence {
        AssertUtils.allDomainPropNotNull("route recovery evidence is incomplete",
                rebuiltRouteCount, removedOldRouteCount, residualOldRouteCount,
                checksumsMatch);
    }
}
