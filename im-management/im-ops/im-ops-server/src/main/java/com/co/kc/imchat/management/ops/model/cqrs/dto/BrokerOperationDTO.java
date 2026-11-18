package com.co.kc.imchat.management.ops.model.cqrs.dto;

import com.co.kc.imchat.management.audit.sdk.annotation.AuditAttribute;

import java.time.Instant;

/** Ops 应用层可见的 Broker operation 结果。 */
public record BrokerOperationDTO(
        String operationId,
        String status,
        Long acceptedEpoch,
        Long targetEpoch,
        @AuditAttribute(include = false)
        String leaderEndpoint,
        @AuditAttribute(include = false)
        String message) {

    /** operation 查询投影；UNKNOWN 不表示原请求未提交。 */
    public record Query(String operationId, String type, String targetBrokerId, String phase,
                        Long acceptedEpoch, Long resultEpoch, String error,
                        Instant createdAt, Instant completedAt) {
    }
}
