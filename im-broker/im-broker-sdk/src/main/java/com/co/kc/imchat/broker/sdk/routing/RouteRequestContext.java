package com.co.kc.imchat.broker.sdk.routing;

import java.io.Serializable;

/**
 * 一次 RouteBroker 调用在定向重试期间保持稳定的路由上下文。
 */
public record RouteRequestContext(long routingEpoch,
                                  String operationId,
                                  long deadlineEpochMillis) implements Serializable {

    public RouteRequestContext {
        if (routingEpoch < 0L) {
            throw new IllegalArgumentException("routingEpoch must not be negative");
        }
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must not be blank");
        }
        if (deadlineEpochMillis <= 0L) {
            throw new IllegalArgumentException("deadlineEpochMillis must be positive");
        }
    }

    public RouteRequestContext withRoutingEpoch(long nextRoutingEpoch) {
        return new RouteRequestContext(nextRoutingEpoch, operationId, deadlineEpochMillis);
    }
}
