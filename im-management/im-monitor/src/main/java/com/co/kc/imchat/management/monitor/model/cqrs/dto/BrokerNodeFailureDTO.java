package com.co.kc.imchat.management.monitor.model.cqrs.dto;

import com.co.kc.imchat.management.monitor.domain.model.NodeStatus;

/** 单个 Broker 查询失败信息。 */
public record BrokerNodeFailureDTO(String brokerId, NodeStatus status, String errorSummary) {
}
