package com.co.kc.imchat.broker.sdk.routing;

/**
 * 可由 SDK 补齐 RouteBroker 调用上下文的请求。
 */
public interface RoutableBrokerRequest<T extends RoutableBrokerRequest<T>> {

    Long userId();

    RouteRequestContext routeContext();

    T withRouteContext(RouteRequestContext routeContext);
}
