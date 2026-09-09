package com.co.kc.imchat.management.monitor.model.cqrs.dto;

import com.co.kc.imchat.management.monitor.domain.model.NodeStatus;

import java.time.Instant;

/** 单个 Broker 的查询状态和总览数据。 */
public record BrokerNodeOverviewDTO(
        String brokerId,
        NodeStatus status,
        BrokerOverviewDTO data,
        String errorSummary,
        Instant queriedAt) {
}
