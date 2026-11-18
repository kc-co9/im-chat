package com.co.kc.imchat.broker.model.io;

/** Broker 管理操作的可选参数。 */
public record BrokerManagementOperationRequest(
        String membershipEndpoint,
        String reason) {
}
