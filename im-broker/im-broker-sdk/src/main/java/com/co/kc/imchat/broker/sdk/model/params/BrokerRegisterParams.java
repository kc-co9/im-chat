package com.co.kc.imchat.broker.sdk.model.params;

import java.io.Serializable;

/**
 * Broker 实例注册请求。
 *
 * @param brokerId Broker 实例 ID
 * @param host     Broker Bolt 主机
 * @param port     Broker Bolt 端口
 */
public record BrokerRegisterParams(String brokerId, String host, int port) implements Serializable {
}
