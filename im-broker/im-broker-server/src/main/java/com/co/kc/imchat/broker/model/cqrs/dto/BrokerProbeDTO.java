package com.co.kc.imchat.broker.model.cqrs.dto;

/**
 * Broker 观察应用输出；不包含 SDK 毫秒舍入或字符串状态。
 */
public record BrokerProbeDTO(
        /* 集群身份。 */
        String clusterId,
        /* 响应者身份。 */
        String brokerId,
        /* 提交版本。 */
        Long revision,
        /* 路由代次。 */
        Long epoch,
        /* 本轮关联身份。 */
        String nonce) {
}
