package com.co.kc.imchat.broker.model.cqrs.result;

/** RouteLookup 返回的一条只读 Gateway 路由位置。 */
public record RouteLocation(String gatewayId,
                            String gatewayHost,
                            int gatewayPort,
                            long routeRevision,
                            long observedEpoch) {
    public RouteLocation {
        if (gatewayId == null || gatewayId.isBlank()
                || gatewayHost == null || gatewayHost.isBlank()
                || gatewayPort <= 0 || routeRevision < 0L
                || observedEpoch < 0L) {
            throw new IllegalArgumentException("invalid route location");
        }
    }
}
