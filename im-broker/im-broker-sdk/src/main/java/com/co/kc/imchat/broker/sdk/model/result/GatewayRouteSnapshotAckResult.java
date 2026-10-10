package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;

/** Gateway 整轮快照确认写入 drain operation 的结果。 */
public record GatewayRouteSnapshotAckResult(Status status, String message)
        implements Serializable {

    public enum Status {
        ACKNOWLEDGED,
        NOT_REQUIRED,
        NOT_LEADER,
        REJECTED
    }

    public GatewayRouteSnapshotAckResult {
        if (status == null) {
            throw new IllegalArgumentException("Gateway route snapshot ACK status is required");
        }
    }

    public static GatewayRouteSnapshotAckResult notRequired() {
        return new GatewayRouteSnapshotAckResult(Status.NOT_REQUIRED, null);
    }

    /** 确认已由共识接受。 */
    public static GatewayRouteSnapshotAckResult acknowledged() {
        return new GatewayRouteSnapshotAckResult(Status.ACKNOWLEDGED, null);
    }

    /** 当前节点不能提交确认，返回已知 Leader 地址。 */
    public static GatewayRouteSnapshotAckResult notLeader(String leaderEndpoint) {
        return new GatewayRouteSnapshotAckResult(Status.NOT_LEADER, leaderEndpoint);
    }

    /** 本次确认不能接受，保留具体原因。 */
    public static GatewayRouteSnapshotAckResult rejected(String message) {
        return new GatewayRouteSnapshotAckResult(Status.REJECTED, message);
    }
}
