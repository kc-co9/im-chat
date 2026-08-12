package com.co.kc.imchat.broker.domain.registry.connection;

import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;

import java.util.List;

/**
 * Broker 用户网关路由索引。
 * <p>
 * 维护用户和用户所在网关的映射关系。
 */
public interface ConnectionRegistry {

    /**
     * 保存用户网关路由。
     *
     * @param userId    用户 ID
     * @param gatewayId 网关实例 ID
     */
    void register(Long userId, String gatewayId);

    /**
     * 删除用户网关路由。
     *
     * @param userId    用户 ID
     * @param gatewayId 网关实例 ID
     */
    void unregister(Long userId, String gatewayId);

    /**
     * 查询用户当前所在网关。
     *
     * @param userId 用户 ID
     * @return 用户网关路由列表
     */
    List<UserGatewayDTO> find(Long userId);

    /**
     * 查询当前 Broker 本地保存的所有用户网关路由。
     *
     * @return 用户网关路由列表
     */
    List<UserGatewayDTO> list();

    /**
     * 保留网关上报的用户集合，并清理该网关下未上报的过期连接。
     *
     * @param gatewayId 网关实例 ID
     * @param userIds   当前有效用户 ID 列表
     * @return 被清理的用户网关路由
     */
    List<UserGatewayDTO> sync(String gatewayId, List<Long> userIds);
}
