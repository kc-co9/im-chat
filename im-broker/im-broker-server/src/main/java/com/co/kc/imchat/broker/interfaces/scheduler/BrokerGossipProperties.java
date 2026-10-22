package com.co.kc.imchat.broker.interfaces.scheduler;

import java.util.List;
import java.time.Duration;

/** Broker 定时 Gossip 所需的本机身份、种子节点及单轮调用限制。 */
public record BrokerGossipProperties(
        /* 本机 Broker 身份。 */ String brokerId,
        /* 本机 RPC 地址。 */ String localAddress,
        /* 尚未发现远端 Broker 时的引导地址。 */ List<String> seedAddresses,
        /* 每轮最多选择的 peer 数量。 */ int fanout,
        /* 单个 peer Gossip 调用超时。 */ Duration timeout) {
}
