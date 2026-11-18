package com.co.kc.imchat.management.ops.infrastructure.client.model;

/** 权威 Broker 节点状态载荷。 */
public record BrokerClusterNodePayload(
        String brokerId,
        String routeEndpoint,
        String membershipRole,
        String routingState) {
}
