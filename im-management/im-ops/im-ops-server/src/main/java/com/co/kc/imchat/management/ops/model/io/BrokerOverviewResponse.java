package com.co.kc.imchat.management.ops.model.io;

import com.co.kc.imchat.management.ops.model.enums.OpsNodeStatusEnum;

/** 单个 Broker 查询总览响应。 */
public record BrokerOverviewResponse(String brokerId, OpsNodeStatusEnum status,
                                     BrokerOverviewDataResponse data, String errorSummary,
                                     Long queriedAt) {
}
