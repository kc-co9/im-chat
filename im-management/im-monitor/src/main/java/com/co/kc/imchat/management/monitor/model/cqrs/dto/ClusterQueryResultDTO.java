package com.co.kc.imchat.management.monitor.model.cqrs.dto;

import java.time.Instant;
import java.util.List;

/** 带来源数据和节点失败信息的集群查询结果。 */
public record ClusterQueryResultDTO<T>(
        List<SourcedValueDTO<T>> values,
        List<BrokerNodeFailureDTO> failures,
        Instant queriedAt) {
}
