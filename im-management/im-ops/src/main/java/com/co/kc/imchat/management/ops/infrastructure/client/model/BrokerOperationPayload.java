package com.co.kc.imchat.management.ops.infrastructure.client.model;

/** Broker 管理 operation 协议结果。 */
public record BrokerOperationPayload(
        String operationId,
        String status,
        long acceptedEpoch,
        Long targetEpoch,
        String leaderEndpoint,
        String message) {
}
