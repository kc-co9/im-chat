package com.co.kc.imchat.broker.model.io;


/** Broker 注册快照响应。 */
public record BrokerNodeResponse(
        /* Broker 标识。 */
        String brokerId,
        /* Broker 主机。 */
        String host,
        /* Broker Bolt 端口。 */
        Integer port,
        /* 注册时间。 */
        Long registeredAtMs,
        /* 最后心跳时间。 */
        Long lastSeenAtMs
) {
}
