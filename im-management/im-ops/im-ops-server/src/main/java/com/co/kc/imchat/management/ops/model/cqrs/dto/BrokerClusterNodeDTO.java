package com.co.kc.imchat.management.ops.model.cqrs.dto;

/** Ops 应用层可见的权威 Broker 节点状态。 */
public record BrokerClusterNodeDTO(
        String brokerId,
        String routeEndpoint,
        String membershipRole,
        String routingState) {
}
