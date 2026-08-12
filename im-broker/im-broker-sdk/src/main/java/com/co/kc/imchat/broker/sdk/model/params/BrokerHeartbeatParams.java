package com.co.kc.imchat.broker.sdk.model.params;

/**
 * Broker 实例心跳请求。
 *
 * @param brokerId Broker 实例 ID
 */
public record BrokerHeartbeatParams(String brokerId) {
}
