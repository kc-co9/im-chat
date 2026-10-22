package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import java.io.Serializable;

/**
 * 当前 voter 集合下故障观察证据所需的多数门槛值对象。
 *
 * <p>BrokerFailureObservationService 使用它判断目标节点是否获得足够的独立不可达观察；
 * 该数量由当前 voter 拓扑计算，不等同于整个集群节点总数，也不等同于 Raft 提交所需的
 * 多数派日志确认数。</p>
 */
public record ObservationQuorum(Integer value) implements Serializable {

    public ObservationQuorum {
        AssertUtils.domainPropNotNull("required observation reports is required", value);
        AssertUtils.domainPropTrue("required observation reports must be positive",
                value >= 1);
    }
}
