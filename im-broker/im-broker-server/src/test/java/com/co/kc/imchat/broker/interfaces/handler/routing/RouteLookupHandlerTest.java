package com.co.kc.imchat.broker.interfaces.handler.routing;

import com.co.kc.imchat.broker.transformer.infrastructure.RegistryTransformer;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;

import com.co.kc.imchat.broker.domain.connection.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import java.time.Instant;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;

import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.support.BrokerClusterConsensusStub;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.sdk.model.params.RouteLookupParams;
import com.co.kc.imchat.broker.sdk.model.result.RouteLookupResult;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.broker.support.BrokerRequestChainTestSupport;
import com.co.kc.imchat.broker.support.TestClock;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteLookupHandlerTest {

    @Test
    void returnsOnlyLocalFreshRouteDescriptions() throws Exception {
        long now = System.currentTimeMillis();
        InMemoryConnectionRegistry connections = new InMemoryConnectionRegistry();
        InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
        gateways.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gateway-a", "10.0.0.8", 18012, "spiffe://gateway/a"));
        connections.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(42L), new GatewayId("gateway-a")), new RouteRevision(9L), Instant.ofEpochMilli(Long.MAX_VALUE)));
        RouteLookupHandler handler = new RouteLookupHandler(connections, gateways,
                new FixedRepository(state()), Duration.ofMinutes(1).toMillis(), TestClock.fixed(now));

        RouteLookupResult result = (RouteLookupResult) BrokerRequestChainTestSupport.passThroughChain(handler).handle(requestContext(),
                JsonUtils.toJson(new RouteLookupParams(
                        "broker-a", 7L, 42L, now + 300L, 1)));

        assertThat(result.routes()).singleElement().satisfies(route -> {
            assertThat(route.gatewayId()).isEqualTo("gateway-a");
            assertThat(route.gatewayHost()).isEqualTo("10.0.0.8");
            assertThat(route.routeRevision()).isEqualTo(9L);
            assertThat(route.observedEpoch()).isEqualTo(7L);
        });
    }

    @Test
    void rejectsRecursiveOrExpiredLookup() {
        long now = System.currentTimeMillis();
        RouteLookupHandler handler = new RouteLookupHandler(
                new InMemoryConnectionRegistry(), new InMemoryGatewayRegistry(),
                new FixedRepository(state()), Duration.ofMinutes(1).toMillis(), TestClock.fixed(now));

        assertThatThrownBy(() -> BrokerRequestChainTestSupport.passThroughChain(handler).handle(requestContext(), JsonUtils.toJson(
                new RouteLookupParams(
                        "broker-a", 7L, 42L, now + 300L, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrokerRequestChainTestSupport.passThroughChain(handler).handle(requestContext(), JsonUtils.toJson(
                new RouteLookupParams(
                        "broker-a", 7L, 42L, now, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BoltRequestContext requestContext() {
        return new BoltRequestContext("127.0.0.1:12345", "127.0.0.1",
                "spiffe://im-chat/broker/broker-a");
    }


    private BrokerClusterState state() {
        return new BrokerClusterState(7L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("broker-a", "127.0.0.1:18020",
                        BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
    }

    private record FixedRepository(BrokerClusterState committedState)
            implements BrokerClusterConsensusStub {
        @Override
        public Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId) {
            return Optional.empty();
        }

        @Override
        public CompletionStage<BrokerClusterConsensusResult> submitChange(BrokerClusterConsensusCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
