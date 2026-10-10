package com.co.kc.imchat.broker.sdk.model.params;

import com.co.kc.imchat.broker.sdk.access.GatewayRouteRequest;

import com.co.kc.imchat.broker.sdk.routing.RoutableBrokerRequest;
import com.co.kc.imchat.broker.sdk.routing.RouteRequestContext;

import java.io.Serializable;

/**
 * 用户网关路由注销请求。
 *
 * @param userId    连接归属用户 ID
 * @param gatewayId 用户所在 WS 网关实例 ID
 * @param routeRevision 当前 Gateway 进程内的连接路由修订号
 */
public record ConnectionUnregisterParams(Long userId,
                                         String gatewayId,
                                         long routeRevision,
                                         RouteRequestContext routeContext)
        implements Serializable, RoutableBrokerRequest<ConnectionUnregisterParams>, GatewayRouteRequest {

    public ConnectionUnregisterParams(Long userId, String gatewayId, long routeRevision) {
        this(userId, gatewayId, routeRevision, null);
    }

    @Override
    public ConnectionUnregisterParams withRouteContext(RouteRequestContext context) {
        return new ConnectionUnregisterParams(userId, gatewayId, routeRevision, context);
    }
}
