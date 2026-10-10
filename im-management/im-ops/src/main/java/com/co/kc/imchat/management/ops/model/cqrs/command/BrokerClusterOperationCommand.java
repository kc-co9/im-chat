package com.co.kc.imchat.management.ops.model.cqrs.command;

/** im-ops 发起 Broker 集群操作所需的稳定幂等参数。 */
public record BrokerClusterOperationCommand(
        String brokerId,
        String action,
        String operationId,
        long expectedEpoch,
        String membershipEndpoint,
        String reason,
        String scope) {

    public BrokerClusterOperationCommand {
        requireText(brokerId, "brokerId");
        requireText(action, "action");
        requireText(operationId, "operationId");
        requireText(scope, "scope");
        if (expectedEpoch < 0L) {
            throw new IllegalArgumentException("expectedEpoch must not be negative");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
