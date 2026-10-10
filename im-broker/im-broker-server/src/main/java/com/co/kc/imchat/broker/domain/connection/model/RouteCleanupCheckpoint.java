package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 本机已经完成的单 Gateway 清理事实，保存确认水位及首次删除数量。
 * 重复请求只能复用相同水位；事实的缓存保留、容量和线程协调由 Registry 实现管理。
 */
public record RouteCleanupCheckpoint(
        /* 已完成删除覆盖的修订水位。 */ RouteRevision routeRevision,
        /* 首次执行删除的路由数量。 */ RouteCount removedRouteCount) {
    public RouteCleanupCheckpoint {
        AssertUtils.allDomainPropNotNull("route cleanup checkpoint is incomplete", routeRevision, removedRouteCount);
    }

    /** 核对重复清理的水位，不允许用不同水位复用首次结果。 */
    public void checkRevision(RouteRevision revision) {
        if (!routeRevision.equals(revision)) {
            throw new IllegalArgumentException("drain cleanup watermark changed");
        }
    }
}
