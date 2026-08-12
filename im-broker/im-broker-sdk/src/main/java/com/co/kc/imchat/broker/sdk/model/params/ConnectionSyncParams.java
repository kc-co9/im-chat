package com.co.kc.imchat.broker.sdk.model.params;

import java.util.List;

/**
 * 网关用户连接快照同步请求。
 * <p>
 * WS 网关定时上报当前仍然活跃的用户，Broker 据此清理过期连接。
 *
 * @param gatewayId 网关实例 ID
 * @param userIds   当前网关内仍然活跃的用户 ID 列表
 */
public record ConnectionSyncParams(String gatewayId, List<Long> userIds) {
}
