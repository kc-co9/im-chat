package com.co.kc.imchat.broker.sdk.model.dto;

import java.time.Instant;

/**
 * Broker 实例端点。
 *
 * @param brokerId     Broker 实例 ID
 * @param host         Broker Bolt 主机
 * @param port         Broker Bolt 端口
 * @param registeredAt 注册时间
 * @param lastSeenAt   最近活跃时间
 */
public record BrokerEndpointDTO(String brokerId,
                                String host,
                                int port,
                                Instant registeredAt,
                                Instant lastSeenAt) {

    /**
     * 获取 Broker Bolt 服务地址。
     *
     * @return host:port 格式的服务地址
     */
    public String address() {
        return host + ":" + port;
    }
}
