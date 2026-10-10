package com.co.kc.imchat.management.ops.model.io;

import com.co.kc.imchat.management.ops.model.enums.OpsNodeStatusEnum;

/** Broker 查询失败响应。 */
public record BrokerFailureResponse(String brokerId, OpsNodeStatusEnum status,
                                    String errorSummary) {
}
