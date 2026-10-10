package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * 当前拓扑下允许自动故障摘流所需的独立 voter 报告数值对象。
 *
 * <p>BrokerFailureObservationService 使用它判断目标节点是否获得足够的独立不可达观察；
 * 该数量由当前 voter 拓扑计算，不等同于整个集群节点总数，也不等同于 Raft 提交所需的
 * 多数派日志确认数。</p>
 */
public record RequiredObservationReports(Integer value) implements Serializable {

    public RequiredObservationReports {
        AssertUtils.domainPropNotNull("required observation reports is required", value);
        AssertUtils.domainPropTrue("required observation reports must be positive",
                value >= 1);
    }
}
