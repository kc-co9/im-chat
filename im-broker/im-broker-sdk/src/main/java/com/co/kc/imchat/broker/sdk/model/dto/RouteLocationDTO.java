package com.co.kc.imchat.broker.sdk.model.dto;

import java.io.Serializable;

/** RouteLookup 返回的只读 Gateway 路由描述。 */
public record RouteLocationDTO(String gatewayId,
                               String gatewayHost,
                               int gatewayPort,
                               long routeRevision,
                               long observedEpoch) implements Serializable {
}
