package com.co.kc.imchat.broker.sdk.model.params;

/**
 * Broker 实例注销请求。
 *
 * @param brokerId Broker 实例 ID
 */
public record BrokerUnregisterParams(String brokerId) {
}
