package com.co.kc.imchat.broker.sdk.model.params;

import java.io.Serializable;

/**
 * Broker 实例心跳请求。
 *
 * @param brokerId Broker 实例 ID
 */
public record BrokerHeartbeatParams(String brokerId) implements Serializable {
}
