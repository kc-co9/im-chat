package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 旧路由清理产生的领域结果，保存删除数量、残留数量和清理校验结果。
 * 应用服务将它与 Gateway 确认的重建数量合并为 RouteRecoveryEvidence，再提交排空证据。
 * 本结果不携带 RPC 状态，也不表示排空已经完成。
 */
public record RouteCleanupResult(
        /* 本次从旧 Broker 删除的路由数量。 */ RouteCount removedRouteCount,
        /* 清理后仍残留在旧 Broker 的路由数量。 */ RouteCount residualRouteCount,
        /* 清理响应的摘要校验是否通过。 */ Boolean checksumsMatch) {

    public RouteCleanupResult {
        AssertUtils.allDomainPropNotNull("route cleanup result is incomplete",
                removedRouteCount, residualRouteCount, checksumsMatch);
    }
}
