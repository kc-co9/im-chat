package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * RouteBroker 路由权威代次值对象。
 *
 * <p>当参与路由选择的 Broker 集合或有效拓扑发生变化时，集群递增 epoch。租约、fence 和
 * 路由副作用都必须绑定同一个 epoch；旧 epoch 即使仍能到达节点，也不能继续执行新的路由
 * 写入或投递。</p>
 */
public record RoutingEpoch(/* RouteBroker 选择输入变化后递增的权威代次。 */ Long value) implements Serializable {
    public RoutingEpoch {
        AssertUtils.domainPropNotNull("routing epoch is required", value);
        AssertUtils.domainPropTrue("routing epoch must not be negative", value >= 0L);
    }

    public RoutingEpoch next() {
        return new RoutingEpoch(Math.addExact(value, 1L));
    }
}
