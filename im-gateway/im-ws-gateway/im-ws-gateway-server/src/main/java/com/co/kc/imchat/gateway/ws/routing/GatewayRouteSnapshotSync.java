package com.co.kc.imchat.gateway.ws.routing;

/** 触发当前 Gateway 完整连接路由水位同步。 */
@FunctionalInterface
public interface GatewayRouteSnapshotSync {
    void synchronize();
}
