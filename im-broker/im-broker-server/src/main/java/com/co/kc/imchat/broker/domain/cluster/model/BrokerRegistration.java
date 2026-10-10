package com.co.kc.imchat.broker.domain.cluster.model;

import java.time.Instant;

/** 注册表中 Broker 的本机运行观察，不表示 Raft 成员或权威路由资格。 */
public record BrokerRegistration(
        /* 注册观察所属 Broker。 */ String brokerId,
        /* Broker 内部 RPC 主机。 */ String host,
        /* Broker 内部 RPC 端口。 */ Integer port,
        /* 首次注册时间。 */ Instant registeredAt,
        /* 最近活跃水位。 */ Instant lastSeenAt) {
    /** Broker 内部 RPC 地址。 */
    public String address() {
        return host + ":" + port;
    }
}
