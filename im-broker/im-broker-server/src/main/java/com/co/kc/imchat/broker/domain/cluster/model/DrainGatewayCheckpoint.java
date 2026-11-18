package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteSnapshotDigest;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * drain 开始时被冻结的 Gateway 及其目标 epoch 快照确认值对象。
 *
 * <p>检查点先记录 Gateway 注册水位，再等待 Gateway 返回 route revision、路由数量和内容
 * 摘要。确认信息必须整体出现；同一 revision 只能绑定同一数量和摘要，较旧 revision
 * 不能覆盖已经接受的确认。</p>
 */
public record DrainGatewayCheckpoint(
        /* 被本次 operation 冻结的 Gateway。 */
        GatewayId gatewayId,
        /* 冻结时观察到的 Gateway 注册水位。 */
        GatewayRegistrationWatermark registrationWatermark,
        /* Gateway 完整快照确认的 route revision。 */
        RouteRevision confirmedRouteRevision,
        /* 对应 revision 的连接路由数量。 */
        RouteCount confirmedRouteCount,
        /* 对应 revision 的稳定内容摘要。 */
        GatewayRouteSnapshotDigest contentChecksum) implements Serializable {

    public DrainGatewayCheckpoint {
        AssertUtils.allDomainPropNotNull("drain Gateway checkpoint identity is incomplete",
                gatewayId, registrationWatermark);
        boolean confirmed = confirmedRouteRevision != null;
        AssertUtils.domainPropTrue("drain Gateway confirmation must be complete",
                confirmed == (confirmedRouteCount != null)
                        && confirmed == (contentChecksum != null));
    }

    public static DrainGatewayCheckpoint pending(GatewayId gatewayId,
                                                 GatewayRegistrationWatermark registrationWatermark) {
        return new DrainGatewayCheckpoint(gatewayId, registrationWatermark,
                null, null, null);
    }

    public boolean confirmed() {
        return confirmedRouteRevision != null;
    }

    public DrainGatewayCheckpoint confirm(RouteRevision routeRevision, RouteCount routeCount,
                                          GatewayRouteSnapshotDigest checksum) {
        DrainGatewayCheckpoint candidate = new DrainGatewayCheckpoint(
                gatewayId, registrationWatermark, routeRevision, routeCount, checksum);
        if (!confirmed()) {
            return candidate;
        }
        if (routeRevision.compareTo(confirmedRouteRevision) < 0) {
            throw new IllegalArgumentException("drain Gateway revision cannot move backwards");
        }
        if (routeRevision.equals(confirmedRouteRevision) && !equals(candidate)) {
            throw new IllegalArgumentException(
                    "drain Gateway revision is already bound to another summary");
        }
        return routeRevision.equals(confirmedRouteRevision) ? this : candidate;
    }
}
