package com.co.kc.imchat.management.monitor.model.io;

import com.co.kc.imchat.management.monitor.model.enums.MonitorNodeStatusEnum;

/** 单个 Broker 查询总览响应。 */
public record BrokerOverviewResponse(String brokerId, MonitorNodeStatusEnum status,
                                     BrokerOverviewDataResponse data, String errorSummary,
                                     Long queriedAt) {
}
