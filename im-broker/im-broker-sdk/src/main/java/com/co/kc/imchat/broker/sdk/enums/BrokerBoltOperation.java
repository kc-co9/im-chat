package com.co.kc.imchat.broker.sdk.enums;

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
    REGISTER_BROKER(BrokerBoltService.BROKER, "registerBroker"),

    /**
     * 注销 Broker 实例。
     */
    UNREGISTER_BROKER(BrokerBoltService.BROKER, "unregisterBroker"),

    /**
     * 刷新 Broker 实例活跃时间。
     */
    HEARTBEAT_BROKER(BrokerBoltService.BROKER, "heartbeatBroker"),

    /**
     * 查询 Broker 实例快照。
     */
    LIST_BROKERS(BrokerBoltService.BROKER, "listBrokers"),

    /**
     * 交换 Gossip 摘要。
     */
    GOSSIP_DIGEST(BrokerBoltService.GOSSIP, "gossipDigest"),

    /**
     * 同步 Gossip 增量。
     */
    GOSSIP_DELTA(BrokerBoltService.GOSSIP, "gossipDelta"),

    /**
     * 注册 WS 网关实例及其内部推送地址。
     */
    REGISTER_GATEWAY(BrokerBoltService.GATEWAY, "registerGateway"),

    /**
     * 注销 WS 网关实例及其持有的连接。
     */
    UNREGISTER_GATEWAY(BrokerBoltService.GATEWAY, "unregisterGateway"),

    /**
     * 刷新 WS 网关实例活跃时间。
     */
    HEARTBEAT_GATEWAY(BrokerBoltService.GATEWAY, "heartbeatGateway"),

    /**
     * 注册单个用户连接到 Broker 连接索引。
     */
    REGISTER_CONNECTION(BrokerBoltService.CONNECTION, "registerConnection"),

    /**
     * 从 Broker 连接索引注销单个用户连接。
     */
    UNREGISTER_CONNECTION(BrokerBoltService.CONNECTION, "unregisterConnection"),

    /**
     * 同步指定 WS 网关当前持有的连接快照。
     */
    SYNC_CONNECTIONS(BrokerBoltService.CONNECTION, "syncConnections"),

    /**
     * 迁移用户连接到新的 owner Broker。
     */
    MIGRATE_CONNECTIONS(BrokerBoltService.CONNECTION, "migrateConnections"),

    /**
     * 统一处理实时帧，上行帧转交业务服务，下行帧写入目标用户在线连接。
     */
    WRITE_FRAME(BrokerBoltService.FRAME, "writeFrame");

    private final BrokerBoltService service;
    private final String operation;

    BrokerBoltOperation(BrokerBoltService service, String operation) {
        this.service = service;
        this.operation = operation;
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
}
