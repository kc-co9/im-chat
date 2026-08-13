package com.co.kc.imchat.broker.sdk.model.dto;

import java.io.Serializable;

/**
 * 待迁移的用户网关路由。
 *
 * @param userId    用户 ID
 * @param gatewayId 用户所在网关实例 ID
 */
public record ConnectionMigrationDTO(Long userId, String gatewayId) implements Serializable {
}
