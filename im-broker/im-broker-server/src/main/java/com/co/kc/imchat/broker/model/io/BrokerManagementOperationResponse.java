package com.co.kc.imchat.broker.model.io;

/** 可恢复 Broker 管理 operation 的提交结果。 */
public record BrokerManagementOperationResponse(
        String operationId,
        String status,
        Long acceptedEpoch,
        Long targetEpoch,
        String leaderEndpoint,
        String message) {
}
