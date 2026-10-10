package com.co.kc.imchat.broker.interfaces.handler.routing;

import com.co.kc.imchat.broker.domain.connection.model.UserId;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensus;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.dto.RouteLocationDTO;
import com.co.kc.imchat.broker.sdk.model.params.RouteLookupParams;
import com.co.kc.imchat.broker.sdk.model.result.RouteLookupResult;

import java.util.List;
import java.time.Clock;

/** 只读取本机 ConnectionRegistry 的非递归 RouteLookup responder。 */
public final class RouteLookupHandler
        extends AbstractBrokerRpcHandler<RouteLookupParams, RouteLookupResult> {
    private final ConnectionRegistry connectionRegistry;
    private final GatewayRegistry gatewayRegistry;
    private final BrokerClusterConsensus stateRepository;
    private final long gatewayTtlMillis;
    private final Clock clock;

    public RouteLookupHandler(ConnectionRegistry connectionRegistry,
                              GatewayRegistry gatewayRegistry,
                              BrokerClusterConsensus stateRepository,
                              long gatewayTtlMillis,
                              Clock clock) {
        super(BrokerBoltOperation.LOOKUP_CONNECTION_ROUTES);
        if (connectionRegistry == null || gatewayRegistry == null
                || stateRepository == null || clock == null || gatewayTtlMillis < 1L) {
            throw new IllegalArgumentException("invalid RouteLookup handler configuration");
        }
        this.connectionRegistry = connectionRegistry;
        this.gatewayRegistry = gatewayRegistry;
        this.stateRepository = stateRepository;
        this.gatewayTtlMillis = gatewayTtlMillis;
        this.clock = clock;
    }

    @Override
    protected RouteLookupResult process(RouteLookupParams params) {
        if (params == null) {
            throw new IllegalArgumentException("invalid RouteLookup request");
        }
        long now = clock.millis();
        if (params.targetUserId() == null || params.routingEpoch() < 0L
                || params.hopCount() != 1 || now >= params.deadlineEpochMillis()) {
            throw new IllegalArgumentException("invalid RouteLookup request");
        }
        long observedEpoch = stateRepository.committedState().epoch();
        List<RouteLocationDTO> routes = connectionRegistry
                .find(new UserId(params.targetUserId())).stream()
                .map(route -> gatewayRegistry.find(route.gatewayId())
                        .filter(gateway -> now - gateway.lastSeenAt().toEpochMilli()
                                < gatewayTtlMillis)
                        .map(gateway -> new RouteLocationDTO(
                                gateway.gatewayId().value(), gateway.host().value(), gateway.port().value(),
                                route.revision().value(), observedEpoch))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new RouteLookupResult(routes);
    }
}
