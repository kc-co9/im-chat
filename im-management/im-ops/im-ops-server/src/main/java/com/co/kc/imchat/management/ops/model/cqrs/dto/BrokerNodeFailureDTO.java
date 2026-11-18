package com.co.kc.imchat.management.ops.model.cqrs.dto;

import com.co.kc.imchat.management.ops.domain.model.NodeStatus;

/** 单个 Broker 查询失败信息。 */
public record BrokerNodeFailureDTO(String brokerId, NodeStatus status, String errorSummary) {
}
