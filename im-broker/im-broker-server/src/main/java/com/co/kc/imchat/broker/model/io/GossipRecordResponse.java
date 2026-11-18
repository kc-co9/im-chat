package com.co.kc.imchat.broker.model.io;

import com.co.kc.imchat.broker.model.enums.BrokerDiagnosticStatusEnum;


/** Gossip 同步记录响应。 */
public record GossipRecordResponse(
        /* 执行时间。 */
        Long executedAtMs,
        /* 目标 Broker 地址。 */
        String target,
        /* 执行状态。 */
        BrokerDiagnosticStatusEnum status,
        /* 处理条目数。 */
        Integer processedCount,
        /* 执行耗时毫秒数。 */
        Long durationMs,
        /* 错误摘要，可选。 */
        String errorSummary
) {
}
