package com.co.kc.imchat.broker.sdk.model.params;

import java.io.Serializable;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.access.ServiceDataPlaneRequest;
import com.co.kc.imchat.broker.sdk.enums.BrokerFrameDirection;
import com.co.kc.imchat.broker.sdk.routing.RoutableBrokerRequest;
import com.co.kc.imchat.broker.sdk.routing.RouteRequestContext;
import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.common.model.io.FrameResponse;

/**
 * Broker 实时帧处理请求。
 * <p>
 * Broker 对外只暴露统一的帧处理入口：客户端上行进入 inbound，服务端下行进入 outbound。
 *
 * @param direction    实时帧流向
 * @param userId       上行时表示当前连接认证用户，下行时表示目标用户
 * @param connectionId 上行 WS 连接 ID，下行时为空
 * @param request      客户端上行实时帧
 * @param response     服务端下行实时帧
 */
public record BrokerFrameWriteParams(BrokerFrameDirection direction,
                                     Long userId,
                                     String connectionId,
                                     FrameRequest request,
                                     FrameResponse response,
                                     RouteRequestContext routeContext)
        implements Serializable, RoutableBrokerRequest<BrokerFrameWriteParams>, ServiceDataPlaneRequest {

    public static BrokerFrameWriteParams inbound(Long userId, String connectionId, FrameRequest request) {
        return new BrokerFrameWriteParams(BrokerFrameDirection.INBOUND, userId, connectionId,
                request, null, null);
    }

    public static BrokerFrameWriteParams outbound(Long userId, FrameResponse response) {
        return new BrokerFrameWriteParams(BrokerFrameDirection.OUTBOUND, userId, null,
                null, response, null);
    }

    @Override
    public BrokerFrameWriteParams withRouteContext(RouteRequestContext context) {
        return new BrokerFrameWriteParams(direction, userId, connectionId, request, response, context);
    }

    @Override
    public BrokerCallerRole requiredCallerRole() {
        if (direction == null) {
            throw new IllegalArgumentException("frame direction must not be null");
        }
        return direction == BrokerFrameDirection.INBOUND
                ? BrokerCallerRole.GATEWAY : BrokerCallerRole.MESSAGE;
    }
}
