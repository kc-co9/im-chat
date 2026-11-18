package com.co.kc.imchat.management.ops.infrastructure.client.model;

/** Broker 管理 operation 协议结果。 */
public record BrokerOperationPayload(
        String operationId,
        String status,
        Long acceptedEpoch,
        Long targetEpoch,
        String leaderEndpoint,
        String message) {

    /** 已登记 operation 的查询协议，区别于提交结果 status。 */
    public record Query(String operationId, String type, String targetBrokerId, String phase,
                        Long acceptedEpoch, Long resultEpoch, String error,
                        Long createdAtMs, Long completedAtMs) {
    }
}
