package com.co.kc.imchat.management.monitor.model.io;

import java.util.List;

/** Monitor 集群总览响应。 */
public record MonitorOverviewResponse(Long brokerCount, Long gatewayCount, Long connectionCount,
                                      List<BrokerOverviewResponse> nodes, Long queriedAt) {
}
