package com.co.kc.imchat.broker.domain.registry.gateway;

import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;

import java.util.Optional;
import java.util.List;

/**
 * Broker 网关实例索引。
 */
public interface GatewayRegistry {

    /**
     * 保存或刷新网关实例。
     *
     * @param gatewayId 网关实例 ID
     * @param host      网关内部通信地址
     * @param port      网关内部通信端口
     */
    void register(String gatewayId, String host, int port);

    /**
     * 删除网关实例。
     *
     * @param gatewayId 网关实例 ID
     */
    void unregister(String gatewayId);

    /**
     * 刷新网关实例活跃时间。
     *
     * @param gatewayId 网关实例 ID
     */
    void heartbeat(String gatewayId);

    /**
     * 查询网关实例。
     *
     * @param gatewayId 网关实例 ID
     * @return 网关实例
     */
    Optional<GatewayEndpointDTO> find(String gatewayId);

    /**
     * 查询当前 Broker 已知的网关实例快照。
     *
     * @return 不可变网关实例列表
     */
    List<GatewayEndpointDTO> list();
}
