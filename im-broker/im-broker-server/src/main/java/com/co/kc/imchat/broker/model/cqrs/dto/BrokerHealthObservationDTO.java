package com.co.kc.imchat.broker.model.cqrs.dto;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.common.utils.AssertUtils;
import java.time.Duration;

/**
 * Broker 观察应用输出；不包含 SDK 毫秒舍入或字符串状态。
 */
public record BrokerHealthObservationDTO(
        /* 被观察 Broker 身份。 */
        String brokerId,
        /* 显式观察状态。 */
        BrokerHealthStatus status,
        /* 采样年龄。 */
        Duration observationAge,
        /* 连续不可达时长。 */
        Duration unreachableDuration) {
    public BrokerHealthObservationDTO {
        AssertUtils.argNotBlank("brokerId is required", brokerId);
        AssertUtils.argNotNull("status is required", status);
        AssertUtils.argTrue("observationAge must be nonnegative", observationAge != null && !observationAge.isNegative());
        AssertUtils.argTrue("unreachableDuration must be nonnegative", unreachableDuration != null && !unreachableDuration.isNegative());
    }
}
