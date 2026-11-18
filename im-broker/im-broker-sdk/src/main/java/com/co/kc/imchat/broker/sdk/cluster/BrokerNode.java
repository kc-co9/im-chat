package com.co.kc.imchat.broker.sdk.cluster;

import java.io.Serializable;

/**
 * 权威集群状态中的 Broker 节点。
 *
 * @param brokerId       跨重启稳定且全局唯一的 Broker 身份
 * @param routeEndpoint  SDK 和 peer Broker 使用的稳定路由端点
 * @param membershipRole 集群发现或 Raft membership 阶段
 * @param routingState   节点参与连接路由的权威状态
 */
public record BrokerNode(
        String brokerId,
        String routeEndpoint,
        BrokerMembershipRole membershipRole,
        BrokerRoutingState routingState) implements Serializable {

    public BrokerNode {
        if (brokerId == null || brokerId.isBlank()) {
            throw new IllegalArgumentException("brokerId must not be blank");
        }
        if (routeEndpoint == null || routeEndpoint.isBlank()) {
            throw new IllegalArgumentException("routeEndpoint must not be blank");
        }
        if (membershipRole == null) {
            throw new IllegalArgumentException("membershipRole must not be null");
        }
        if (routingState == null) {
            throw new IllegalArgumentException("routingState must not be null");
        }
        if (membershipRole == BrokerMembershipRole.REMOVED) {
            throw new IllegalArgumentException("REMOVED membership must not remain in cluster state");
        }
        if ((membershipRole == BrokerMembershipRole.DISCOVERED
                || membershipRole == BrokerMembershipRole.LEARNER_SYNCING)
                && routingState != BrokerRoutingState.INACTIVE) {
            throw new IllegalArgumentException("membership is not ready to carry routing traffic");
        }
    }

    public boolean participatesInRouting() {
        return routingState == BrokerRoutingState.ACTIVE;
    }
}
