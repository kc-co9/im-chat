package com.co.kc.imchat.broker.sdk.enums;

/**
 * Broker RPC 服务。
 */
public enum BrokerBoltService {
    /**
     * Broker 实例管理服务。
     */
    BROKER("broker.broker"),

    /**
     * Broker 集群 Gossip 同步服务。
     */
    GOSSIP("broker.gossip"),

    /**
     * 网关实例管理服务。
     */
    GATEWAY("broker.gateway"),

    /**
     * 用户连接管理服务。
     */
    CONNECTION("broker.connection"),

    /**
     * 业务实时帧处理服务。
     */
    FRAME("broker.frame");

    private final String service;

    BrokerBoltService(String service) {
        this.service = service;
    }

    /**
     * Bolt RPC service 名称。
     *
     * @return service 名称
     */
    public String service() {
        return service;
    }
}
