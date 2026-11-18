package com.co.kc.imchat.management.ops.model.io;

import java.util.List;

/** 带查询来源的运行投影，不声称线性一致。 */
public record OpsCliClusterResponse(
        /* 生成投影的 Broker。 */
        String sourceBroker,
        /* 运行投影修订版本。 */
        long revision,
        /* 集群成员操作 epoch。 */
        long epoch,
        /* 当前路由算法标识。 */
        String algorithm,
        /* Broker 运行节点列表。 */
        List<Node> brokers) {
    /** CLI 展示的单个 Broker 运行节点。 */
    public record Node(
            /* Broker 身份。 */
            String brokerId,
            /* 路由服务端点。 */
            String routeEndpoint,
            /* 成员角色。 */
            String membershipRole,
            /* 路由运行状态。 */
            String routingState) {
    }
}
