package com.co.kc.imchat.broker.sdk.model.result;

import com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus;
import com.co.kc.imchat.common.utils.AssertUtils;
import java.io.Serializable;

/** observer 在同一单调采样点计算的时长，不传输跨节点 nanoTime。 */
public record BrokerHealthReportRow(
        /* 被观察 Broker 身份。 */
        String brokerId,
        /* 显式健康状态。 */
        BrokerHealthStatus status,
        /* 年龄毫秒，发送时保守向上取整。 */
        Long observationAgeMs,
        /* 连续失败毫秒，发送时向下取整。 */
        Long unreachableDurationMs) implements Serializable {
    public BrokerHealthReportRow {
        AssertUtils.argNotBlank("brokerId is required", brokerId);
        AssertUtils.argNotNull("status is required", status);
        AssertUtils.argTrue("observationAgeMs must be nonnegative", observationAgeMs != null && observationAgeMs >= 0L);
        AssertUtils.argTrue("unreachableDurationMs must be nonnegative", unreachableDurationMs != null && unreachableDurationMs >= 0L);
        AssertUtils.argTrue("healthy or unknown cannot retain unreachable duration", status == BrokerHealthStatus.UNHEALTHY || unreachableDurationMs == 0L);
    }
}
