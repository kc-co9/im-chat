package com.co.kc.imchat.broker.sdk.routing;

import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;

/**
 * 根据已提交集群状态确定用户连接路由所在的 Broker。
 */
public interface RouteBrokerSelector {

    BrokerNode select(long userId, BrokerClusterState clusterState);
}
