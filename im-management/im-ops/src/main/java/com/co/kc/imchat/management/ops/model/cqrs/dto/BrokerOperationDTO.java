package com.co.kc.imchat.management.ops.model.cqrs.dto;

/** Ops 应用层可见的 Broker operation 结果。 */
public record BrokerOperationDTO(
        String operationId,
        String status,
        long acceptedEpoch,
        Long targetEpoch,
        String leaderEndpoint,
        String message) {
}
