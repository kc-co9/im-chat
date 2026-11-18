package com.co.kc.imchat.management.ops.model.io;

import java.util.List;

/** Ops 集群总览响应。 */
public record OpsOverviewResponse(Long brokerCount, Long gatewayCount, Long connectionCount,
                                      List<BrokerOverviewResponse> nodes, Long queriedAt) {
}
