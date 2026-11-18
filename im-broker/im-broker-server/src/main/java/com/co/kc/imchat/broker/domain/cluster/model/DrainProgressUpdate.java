package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteSnapshotDigest;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * BrokerCluster 可以单调合并的 drain 进度事实值对象。
 *
 * <p>一次更新只表达两类事实之一：某个被冻结 Gateway 已确认完整路由快照，或 RouteBroker
 * 已完成一次全局重建/清理汇总。构造校验禁止把两类字段混合，应用时由 {@link DrainProgress}
 * 负责保持确认 revision、摘要和完成条件不回退。</p>
 */
public record DrainProgressUpdate(
        /* 本次进度事实的种类。 */
        Kind kind,
        /* 确认完整快照的 Gateway；汇总事实中为空。 */
        GatewayId gatewayId,
        /* Gateway 已确认的路由修订号。 */
        RouteRevision routeRevision,
        /* Gateway 已确认的连接路由数量。 */
        RouteCount routeCount,
        /* Gateway 已确认内容的稳定摘要。 */
        GatewayRouteSnapshotDigest contentChecksum,
        /* 全部 RouteBroker 的重建与残留汇总。 */
        RouteRecoveryEvidence recoveryEvidence) implements Serializable {

    public enum Kind {
        /** 一个 Gateway 确认完整连接快照。 */
        CONFIRM_GATEWAY,
        /** 汇总新路由重建与旧路由清理证据。 */
        SUMMARIZE
    }

    public DrainProgressUpdate {
        AssertUtils.domainPropNotNull("drain progress update kind is required", kind);
        if (kind == Kind.CONFIRM_GATEWAY) {
            AssertUtils.domainPropTrue("invalid drain Gateway confirmation",
                    gatewayId != null && routeRevision != null && routeCount != null
                            && contentChecksum != null && recoveryEvidence == null);
        } else {
            AssertUtils.domainPropTrue("invalid drain route summary update",
                    recoveryEvidence != null && gatewayId == null && contentChecksum == null
                            && routeRevision == null && routeCount == null);
        }
    }

    public static DrainProgressUpdate confirmGateway(GatewayId gatewayId,
                                                     RouteRevision routeRevision,
                                                     RouteCount routeCount,
                                                     GatewayRouteSnapshotDigest checksum) {
        return new DrainProgressUpdate(Kind.CONFIRM_GATEWAY, gatewayId,
                routeRevision, routeCount, checksum, null);
    }

    public static DrainProgressUpdate summarize(RouteRecoveryEvidence summary) {
        return new DrainProgressUpdate(Kind.SUMMARIZE, null, null, null, null, summary);
    }

    public DrainProgress applyTo(DrainProgress progress) {
        if (progress == null) {
            throw new IllegalStateException("drain progress was not initialized");
        }
        return switch (kind) {
            case CONFIRM_GATEWAY -> progress.confirmGateway(
                    gatewayId, routeRevision, routeCount, contentChecksum);
            case SUMMARIZE -> progress.summarize(recoveryEvidence);
        };
    }
}
