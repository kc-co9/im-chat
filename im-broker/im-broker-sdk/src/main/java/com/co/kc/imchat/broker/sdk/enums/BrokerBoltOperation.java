package com.co.kc.imchat.broker.sdk.enums;

import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;

/**
 * Broker RPC 操作。
 * <p>
 * 每个 operation 固定归属于一个 Broker RPC service，调用方和 Broker Bolt Handler 必须使用同一组 service
 * 与 operation 名称完成注册、连接维护和实时帧处理。
 */
public enum BrokerBoltOperation {
    /**
     * 注册 Broker 实例。
     */
    REGISTER_BROKER(BrokerBoltService.BROKER, "registerBroker", BrokerAdmissionType.BROKER_PEER),

    /**
     * 注销 Broker 实例。
     */
    UNREGISTER_BROKER(BrokerBoltService.BROKER, "unregisterBroker", BrokerAdmissionType.BROKER_PEER),

    /**
     * 刷新 Broker 实例活跃时间。
     */
    HEARTBEAT_BROKER(BrokerBoltService.BROKER, "heartbeatBroker", BrokerAdmissionType.BROKER_PEER),

    /**
     * 查询 Broker 实例快照。
     */
    LIST_BROKERS(BrokerBoltService.BROKER, "listBrokers", BrokerAdmissionType.CLUSTER_READ),

    /**
     * 获取 Raft 已提交的完整 BrokerClusterState。
     */
    GET_CLUSTER_STATE(BrokerBoltService.BROKER, "getClusterState", BrokerAdmissionType.CLUSTER_READ),

    /** 查询目标 Broker 已取得并在本机应用的 quorum ReadIndex，不表示最新应用位置。 */
    FETCH_QUORUM_READ_INDEX(BrokerBoltService.BROKER, "fetchQuorumReadIndex", BrokerAdmissionType.BROKER_CONTROL),

    /**
     * 交换 Gossip 摘要。
     */
    GOSSIP_DIGEST(BrokerBoltService.GOSSIP, "gossipDigest", BrokerAdmissionType.BROKER_PEER),

    /**
     * 同步 Gossip 增量。
     */
    GOSSIP_DELTA(BrokerBoltService.GOSSIP, "gossipDelta", BrokerAdmissionType.BROKER_PEER),

    /**
     * 持久关闭指定 routing epoch 的 Broker 本机权威副作用准入。
     */
    FENCE_ROUTING_EPOCH(BrokerBoltService.BROKER, "fenceRoutingEpoch", BrokerAdmissionType.BROKER_CONTROL),

    /** 在 holder 本机开始当前 epoch Lease 申请。 */
    PREPARE_ROUTE_LEASE(BrokerBoltService.BROKER, "prepareRouteLease", BrokerAdmissionType.BROKER_CONTROL),

    /** 把 quorum-backed Lease grant 安装回原申请 holder。 */
    INSTALL_ROUTE_LEASE(BrokerBoltService.BROKER, "installRouteLease", BrokerAdmissionType.BROKER_CONTROL),

    /**
     * 注册 WS 网关实例及其内部推送地址。
     */
    REGISTER_GATEWAY(BrokerBoltService.GATEWAY, "registerGateway", BrokerAdmissionType.GATEWAY_BOOTSTRAP),

    /**
     * 注销 WS 网关实例及其持有的连接。
     */
    UNREGISTER_GATEWAY(BrokerBoltService.GATEWAY, "unregisterGateway", BrokerAdmissionType.GATEWAY_ROUTE),

    /**
     * 刷新 WS 网关实例活跃时间。
     */
    HEARTBEAT_GATEWAY(BrokerBoltService.GATEWAY, "heartbeatGateway", BrokerAdmissionType.GATEWAY_ROUTE),

    /**
     * 注册单个用户连接到 Broker 连接索引。
     */
    REGISTER_CONNECTION(BrokerBoltService.CONNECTION, "registerConnection", BrokerAdmissionType.GATEWAY_ROUTE),

    /**
     * 从 Broker 连接索引注销单个用户连接。
     */
    UNREGISTER_CONNECTION(BrokerBoltService.CONNECTION, "unregisterConnection", BrokerAdmissionType.GATEWAY_ROUTE),

    /**
     * 同步指定 WS 网关当前持有的连接快照。
     */
    APPLY_GATEWAY_ROUTE_SNAPSHOT(BrokerBoltService.CONNECTION, "applyGatewayRouteSnapshot", BrokerAdmissionType.GATEWAY_ROUTE),

    /** Gateway 完成目标 epoch 整轮分片快照后的聚合确认。 */
    ACK_GATEWAY_ROUTE_SNAPSHOT(BrokerBoltService.CONNECTION, "ackGatewayRouteSnapshot", BrokerAdmissionType.GATEWAY_ROUTE),

    /** Broker peer 只查询本机连接路由，不允许递归或携带业务 Frame。 */
    LOOKUP_CONNECTION_ROUTES(BrokerBoltService.CONNECTION, "lookupConnectionRoutes", BrokerAdmissionType.BROKER_DATA_PLANE),

    /** Leader 请求 DRAINING Broker 按 committed 水位清理旧路由。 */
    CLEANUP_DRAIN_ROUTES(BrokerBoltService.CONNECTION, "cleanupDrainRoutes", BrokerAdmissionType.BROKER_DATA_PLANE),

    /**
     * 迁移用户连接到新的 owner Broker。
     */

    /** 关闭用户连接。 */
    CLOSE_CONNECTIONS(BrokerBoltService.CONNECTION, "closeConnections", BrokerAdmissionType.SERVICE_DATA_PLANE),

    /**
     * 统一处理实时帧，上行帧转交业务服务，下行帧写入目标用户在线连接。
     */
    WRITE_FRAME(BrokerBoltService.FRAME, "writeFrame", BrokerAdmissionType.SERVICE_DATA_PLANE);

    private final BrokerBoltService service;
    private final String operation;
    private final BrokerAdmissionType admissionType;

    BrokerBoltOperation(BrokerBoltService service, String operation,
                        BrokerAdmissionType admissionType) {
        this.service = service;
        this.operation = operation;
        this.admissionType = admissionType;
    }

    /**
     * Bolt RPC service。
     *
     * @return service 枚举
     */
    public BrokerBoltService service() {
        return service;
    }

    /**
     * Bolt RPC operation 名称。
     *
     * @return operation 名称
     */
    public String operation() {
        return operation;
    }

    public BrokerAdmissionType admissionType() {
        return admissionType;
    }
}
