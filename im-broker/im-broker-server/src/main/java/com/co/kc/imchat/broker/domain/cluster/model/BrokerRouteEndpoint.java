package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * Broker 对外提供共识和路由协作能力的稳定端点值对象。
 *
 * <p>该端点用于 SDK、Broker 节点之间的直达调用和 fence/租约协作；它是连接位置，
 * 不是 Broker 的身份。端点变更必须通过集群拓扑规则显式拒绝或创建新的成员事实。</p>
 */
public record BrokerRouteEndpoint(
        /* SDK 和 Broker 直达该节点的稳定网络端点。 */
        String value) implements Serializable {
    public BrokerRouteEndpoint {
        AssertUtils.domainPropNotBlank("broker route endpoint must not be blank", value);
    }
}
