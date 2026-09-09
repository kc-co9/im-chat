package com.co.kc.imchat.management.monitor.model.io;

import com.co.kc.imchat.management.monitor.model.enums.MonitorNodeStatusEnum;

/** Broker 查询失败响应。 */
public record BrokerFailureResponse(String brokerId, MonitorNodeStatusEnum status,
                                    String errorSummary) {
}
