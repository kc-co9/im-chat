package com.co.kc.imchat.broker.sdk.model.params;

import java.io.Serializable;

/**
 * Broker 实例注销请求。
 *
 * @param brokerId Broker 实例 ID
 */
public record BrokerUnregisterParams(String brokerId) implements Serializable {
}
