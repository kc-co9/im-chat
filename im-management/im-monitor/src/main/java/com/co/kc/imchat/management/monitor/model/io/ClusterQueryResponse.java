package com.co.kc.imchat.management.monitor.model.io;

import java.util.List;

/** 多 Broker 聚合查询响应。 */
public record ClusterQueryResponse<T>(List<SourcedResponse<T>> values,
                                      List<BrokerFailureResponse> failures,
                                      Long queriedAt) {
}
