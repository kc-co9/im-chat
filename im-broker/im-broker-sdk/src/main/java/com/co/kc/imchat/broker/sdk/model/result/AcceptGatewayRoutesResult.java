package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;

/**
 * Gateway 路由的接收结果，区分单个分片接受与完整路由应用。
 */
public record AcceptGatewayRoutesResult(
        Status status,
        long routingEpoch,
        long retryAfterMillis) implements Serializable {

    public enum Status {
        CHUNK_ACCEPTED,
        ROUTES_APPLIED,
        ROUTE_MOVED,
        INVALID_BATCH,
        RETRY_AFTER,
        UNAUTHORIZED
    }

    public AcceptGatewayRoutesResult {
        if (status == null || routingEpoch < 0L || retryAfterMillis < 0L) {
            throw new IllegalArgumentException("invalid Gateway route snapshot result");
        }
    }

    public static AcceptGatewayRoutesResult accepted(long routingEpoch) {
        return new AcceptGatewayRoutesResult(Status.CHUNK_ACCEPTED, routingEpoch, 0L);
    }

    public static AcceptGatewayRoutesResult applied(long routingEpoch) {
        return new AcceptGatewayRoutesResult(Status.ROUTES_APPLIED, routingEpoch, 0L);
    }

    /** 分片或完整批次不满足协议条件。 */
    public static AcceptGatewayRoutesResult invalidBatch(long routingEpoch) {
        return new AcceptGatewayRoutesResult(Status.INVALID_BATCH, routingEpoch, 0L);
    }

    /** 目标已不属于当前路由代次。 */
    public static AcceptGatewayRoutesResult routeMoved(long routingEpoch) {
        return new AcceptGatewayRoutesResult(Status.ROUTE_MOVED, routingEpoch, 0L);
    }

    /** 暂无容量或执行许可，调用方在给定间隔后重试。 */
    public static AcceptGatewayRoutesResult retryAfter(long routingEpoch, long retryAfterMillis) {
        return new AcceptGatewayRoutesResult(Status.RETRY_AFTER, routingEpoch, retryAfterMillis);
    }
}
