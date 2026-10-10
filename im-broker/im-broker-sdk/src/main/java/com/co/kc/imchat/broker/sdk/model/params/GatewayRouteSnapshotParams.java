package com.co.kc.imchat.broker.sdk.model.params;

import com.co.kc.imchat.broker.sdk.access.GatewayRouteRequest;

import java.io.Serializable;
import java.util.List;

/**
 * Gateway 按 RouteBroker 分组后的完整连接路由快照分块。
 */
public record GatewayRouteSnapshotParams(
        String gatewayId,
        long routingEpoch,
        long routeRevision,
        String routeBrokerId,
        String batchId,
        int chunkIndex,
        int chunkCount,
        String contentChecksum,
        long eventDeadline,
        List<Long> userIds) implements Serializable, GatewayRouteRequest {

    public GatewayRouteSnapshotParams {
        if (gatewayId == null || gatewayId.isBlank()) {
            throw new IllegalArgumentException("gatewayId must not be blank");
        }
        if (routingEpoch < 0L || routeRevision < 0L) {
            throw new IllegalArgumentException("routingEpoch and routeRevision must not be negative");
        }
        if (routeBrokerId == null || routeBrokerId.isBlank()) {
            throw new IllegalArgumentException("routeBrokerId must not be blank");
        }
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("batchId must not be blank");
        }
        if (chunkCount <= 0 || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("invalid chunk position");
        }
        if (contentChecksum == null || contentChecksum.isBlank()) {
            throw new IllegalArgumentException("contentChecksum must not be blank");
        }
        if (eventDeadline <= 0L) {
            throw new IllegalArgumentException("eventDeadline must be positive");
        }
        if (userIds == null || userIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("userIds must not contain null");
        }
        userIds = List.copyOf(userIds);
    }
}
