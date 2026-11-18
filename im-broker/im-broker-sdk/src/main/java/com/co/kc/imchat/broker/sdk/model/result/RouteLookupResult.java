package com.co.kc.imchat.broker.sdk.model.result;

import com.co.kc.imchat.broker.sdk.model.dto.RouteLocationDTO;

import java.io.Serializable;
import java.util.List;

/** Broker peer RouteLookup 结果，不携带业务 Frame。 */
public record RouteLookupResult(List<RouteLocationDTO> routes) implements Serializable {
    public RouteLookupResult {
        routes = routes == null ? List.of() : List.copyOf(routes);
    }
}
