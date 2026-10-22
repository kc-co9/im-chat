package com.co.kc.imchat.broker.model.cqrs.dto;

import java.util.List;

/**
 * Broker 观察应用输出；不包含 SDK 毫秒舍入或字符串状态。
 */
public record BrokerHealthReportDTO(
        /* 集群身份。 */
        String clusterId,
        /* 报告者身份。 */
        String observerBrokerId,
        /* 提交版本。 */
        Long revision,
        /* 路由代次。 */
        Long epoch,
        /* 本轮关联身份。 */
        String nonce,
        /* 完整不可变观察集合。 */
        List<BrokerHealthObservationDTO> observations) {
    public BrokerHealthReportDTO { observations = List.copyOf(observations); }
}
