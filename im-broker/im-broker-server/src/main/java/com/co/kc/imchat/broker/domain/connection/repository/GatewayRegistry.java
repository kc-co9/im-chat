package com.co.kc.imchat.broker.domain.connection.repository;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;


import java.util.Optional;
import java.util.List;

/**
 * Broker 网关实例索引。
 */
public interface GatewayRegistry {

    /**
     * 保存或刷新网关实例。
     *
     * @param registration 网关注册观察，包含身份、端点、工作负载身份和观察时间
     */
    void register(GatewayRegistration registration);

    /**
     * 删除网关实例。
     *
     * @param gatewayId 网关实例 ID
     */
    void unregister(GatewayId gatewayId);

    /** 查询进程身份是否已被永久退役，退役 identity 不得重新注册。 */
    boolean isRetired(GatewayId gatewayId);

    /**
     * 刷新网关实例活跃时间。
     *
     * @param gatewayId 网关实例 ID
     */
    void heartbeat(GatewayId gatewayId);

    /**
     * 查询网关实例。
     *
     * @param gatewayId 网关实例 ID
     * @return 网关实例
     */
    Optional<GatewayRegistration> find(GatewayId gatewayId);

    /**
     * 查询当前 Broker 已知的网关实例快照。
     *
     * @return 不可变网关实例列表
     */
    List<GatewayRegistration> list();
}
