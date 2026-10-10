package com.co.kc.imchat.broker.model.cqrs.result;

import java.util.List;

/** 有界 RouteLookup 用例结果。 */
public record RouteLookupResult(List<RouteLocation> routes) {
    public RouteLookupResult {
        routes = routes == null ? List.of() : List.copyOf(routes);
    }

    public static RouteLookupResult empty() {
        return new RouteLookupResult(List.of());
    }
}
