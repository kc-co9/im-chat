package com.co.kc.imchat.broker.sdk.model.params;

import com.co.kc.imchat.broker.sdk.access.GatewayRouteRequest;

import java.io.Serializable;

/** Gateway 完成目标 epoch 整轮分片快照后的聚合确认。 */
public record GatewayRouteSnapshotAckParams(
        String gatewayId,
        long routingEpoch,
        long routeRevision,
        long routeCount,
        String contentChecksum,
        long updateExpireAtMs) implements Serializable, GatewayRouteRequest {

    public GatewayRouteSnapshotAckParams {
        if (gatewayId == null || gatewayId.isBlank() || routingEpoch < 0L
                || routeRevision < 0L || routeCount < 0L
                || contentChecksum == null || contentChecksum.isBlank()
                || updateExpireAtMs <= 0L) {
            throw new IllegalArgumentException("invalid Gateway route snapshot ACK");
        }
    }
}
