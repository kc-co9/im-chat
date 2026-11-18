package com.co.kc.imchat.broker.model.cqrs.dto;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.broker.model.enums.DiagnosticStatus;

import java.time.Instant;
import java.time.Duration;

/**
 * 单次 Broker Gossip 同步诊断记录。
 *
 * @param executedAt 执行时间
 * @param target 目标 Broker 地址
 * @param status 执行状态
 * @param processedCount 处理条目数
 * @param duration 耗时
 * @param errorSummary 截断后的错误摘要
 */
public record GossipRecordDTO(
        Instant executedAt,
        String target,
        DiagnosticStatus status,
        Integer processedCount,
        Duration duration,
        String errorSummary
) {
    public GossipRecordDTO {
        AssertUtils.argNotNull("executedAt must not be null", executedAt);
        AssertUtils.argNotBlank("target must not be blank", target);
        AssertUtils.allArgNotNull(
                "diagnostic numeric and status properties must not be null",
                status,
                processedCount,
                duration);
        AssertUtils.argTrue("processedCount must not be negative", processedCount >= 0);
        AssertUtils.argTrue("duration must not be negative", !duration.isNegative());
        if (errorSummary != null && errorSummary.length() > 256) {
            errorSummary = errorSummary.substring(0, 256);
        }
    }
}
