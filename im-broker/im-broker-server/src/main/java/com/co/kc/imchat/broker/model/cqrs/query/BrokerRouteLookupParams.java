package com.co.kc.imchat.broker.model.cqrs.query;

import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.connection.model.UserId;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;

/** 本轮向指定 Broker 查询用户路由所需的应用参数，不是 peer RPC 载荷。 */
public record BrokerRouteLookupParams(
        BrokerNode broker,
        UserId userId,
        RoutingEpoch routingEpoch) {
}
