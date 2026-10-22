package com.co.kc.imchat.broker.infrastructure.interceptor;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerCallerIdentityInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerPeerAdmissionInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerRouteCallerInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.GatewayRegistrationInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.GatewayRouteAuthorizationInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.GatewayHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeaseInstallParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeasePrepareParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLookupParams;
import com.co.kc.imchat.broker.support.BrokerClusterConsensusStub;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.broker.transformer.registry.RegistryTransformer;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDigestParams;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityAccessInterceptorsTest {

    private static final String GATEWAY_PREFIX = "spiffe://im-chat/gateway/";
    private static final String BROKER_PREFIX = "spiffe://im-chat/broker/";
    private final FixedRepository repository = new FixedRepository(state());

    @Test
    void workloadIdentityMustBeAllowedForOperationProfile() throws Exception {
        BrokerCallerIdentityInterceptor interceptor = workloadInterceptor();
        BrokerRequestInvocation<GatewayHeartbeatParams> invocation =
                BrokerRequestInvocation.initial(BrokerBoltOperation.HEARTBEAT_GATEWAY,
                        new GatewayHeartbeatParams("gateway-1"),
                        context(BROKER_PREFIX + "broker-a"));

        assertThatThrownBy(() -> interceptor.intercept(invocation, request -> "unexpected"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("operation");
    }

    @Test
    void gatewayBootstrapKeepsAuthenticatedCallerAndRouteChecksStoredLease() throws Exception {
        GatewayRegistrationInterceptor registration =
                new GatewayRegistrationInterceptor();
        BrokerCallerPrincipal workload = new BrokerCallerPrincipal(
                GATEWAY_PREFIX + "gateway-process-1", BrokerCallerRole.GATEWAY);
        BrokerRequestInvocation<GatewayRegisterParams> register =
                BrokerRequestInvocation.initial(BrokerBoltOperation.REGISTER_GATEWAY,
                        new GatewayRegisterParams("gateway-1", "127.0.0.1", 12000), null)
                        .withPrincipal(workload);

        Object principal = registration.intercept(register, request -> request.principal());

        assertThat(principal).isSameAs(workload);

        InMemoryGatewayRegistry registry = new InMemoryGatewayRegistry();
        registry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gateway-1", "127.0.0.1", 12000, GATEWAY_PREFIX + "gateway-process-1"));
        GatewayRouteAuthorizationInterceptor route = new GatewayRouteAuthorizationInterceptor(
                registry, Duration.ofSeconds(30));
        BrokerRequestInvocation<GatewayHeartbeatParams> heartbeat =
                BrokerRequestInvocation.initial(BrokerBoltOperation.HEARTBEAT_GATEWAY,
                        new GatewayHeartbeatParams("gateway-1"), null)
                        .withPrincipal(workload);

        assertThat(route.intercept(heartbeat, request -> "ok")).isEqualTo("ok");
    }

    @Test
    void gatewayBootstrapRejectsMissingGatewayIdAndNonGatewayCaller() {
        GatewayRegistrationInterceptor registration = new GatewayRegistrationInterceptor();
        BrokerRequestInvocation<GatewayRegisterParams> missingId = BrokerRequestInvocation.initial(
                        BrokerBoltOperation.REGISTER_GATEWAY,
                        new GatewayRegisterParams("", "127.0.0.1", 12000), null)
                .withPrincipal(new BrokerCallerPrincipal(
                        GATEWAY_PREFIX + "gateway-process-1", BrokerCallerRole.GATEWAY));
        BrokerRequestInvocation<GatewayRegisterParams> wrongRole = BrokerRequestInvocation.initial(
                        BrokerBoltOperation.REGISTER_GATEWAY,
                        new GatewayRegisterParams("gateway-1", "127.0.0.1", 12000), null)
                .withPrincipal(new BrokerCallerPrincipal(
                        BROKER_PREFIX + "broker-a", BrokerCallerRole.BROKER));

        assertThatThrownBy(() -> registration.intercept(missingId, request -> "unexpected"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> registration.intercept(wrongRole, request -> "unexpected"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void brokerDataPlaneBindsClaimedRequesterToCommittedTlsIdentity() throws Exception {
        BrokerRouteCallerInterceptor interceptor = new BrokerRouteCallerInterceptor(
                repository.asAdapter(), BROKER_PREFIX, false);
        RouteLookupParams params = new RouteLookupParams(
                "broker-a", 1L, 7L, 2_000L, 1);
        BrokerRequestInvocation<RouteLookupParams> invocation =
                BrokerRequestInvocation.initial(BrokerBoltOperation.LOOKUP_CONNECTION_ROUTES,
                        params, null)
                        .withPrincipal(new BrokerCallerPrincipal(
                                BROKER_PREFIX + "broker-a", BrokerCallerRole.BROKER));

        assertThat(interceptor.intercept(invocation, request -> "ok")).isEqualTo("ok");
    }

    @Test
    void peerInterceptorBindsGossipNodeButTreatsLeaseHolderAsLocalTarget() throws Exception {
        BrokerPeerAdmissionInterceptor interceptor = new BrokerPeerAdmissionInterceptor(
                "broker-b", repository.asAdapter(), BROKER_PREFIX, "cluster");
        BrokerRequestInvocation<GossipDigestParams> gossip =
                BrokerRequestInvocation.initial(BrokerBoltOperation.GOSSIP_DIGEST,
                        new GossipDigestParams("broker-a", List.of()), null)
                        .withPrincipal(new BrokerCallerPrincipal(
                                BROKER_PREFIX + "broker-a", BrokerCallerRole.BROKER));
        assertThat(interceptor.intercept(gossip, request -> "ok")).isEqualTo("ok");

        RouteLeaseInstallParams installParams = new RouteLeaseInstallParams(
                "broker-b", 7L, 3L, 1L,
                "nonce-1", 1_000L, 10L);
        BrokerRequestInvocation<RouteLeaseInstallParams> install =
                BrokerRequestInvocation.initial(BrokerBoltOperation.INSTALL_ROUTE_LEASE,
                        installParams, null)
                        .withPrincipal(new BrokerCallerPrincipal(
                                BROKER_PREFIX + "broker-a", BrokerCallerRole.BROKER));

        assertThat(interceptor.intercept(install, request -> "installed"))
                .isEqualTo("installed");
    }

    @Test
    void controlInterceptorRejectsUnknownRequestType() {
        BrokerPeerAdmissionInterceptor interceptor = new BrokerPeerAdmissionInterceptor(
                "broker-b", repository.asAdapter(), BROKER_PREFIX, "cluster");
        BrokerRequestInvocation<Object> invocation = BrokerRequestInvocation.initial(
                        BrokerBoltOperation.FETCH_QUORUM_READ_INDEX, new Object(), null)
                .withPrincipal(new BrokerCallerPrincipal(
                        BROKER_PREFIX + "broker-a", BrokerCallerRole.BROKER));

        assertThatThrownBy(() -> interceptor.intercept(invocation, request -> "unexpected"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("capability");
        BrokerRequestInvocation<RouteLeasePrepareParams> wrongOperation = BrokerRequestInvocation.initial(
                        BrokerBoltOperation.FETCH_QUORUM_READ_INDEX,
                        new RouteLeasePrepareParams("operation-1", 7L), null)
                .withPrincipal(new BrokerCallerPrincipal(
                        BROKER_PREFIX + "broker-a", BrokerCallerRole.BROKER));
        assertThatThrownBy(() -> interceptor.intercept(wrongOperation, request -> "unexpected"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("capability");
        assertThatCode(() -> interceptor.intercept(
                BrokerRequestInvocation.initial(BrokerBoltOperation.FETCH_QUORUM_READ_INDEX,
                        new QuorumReadIndexParams(), null)
                        .withPrincipal(new BrokerCallerPrincipal(
                                BROKER_PREFIX + "broker-a", BrokerCallerRole.BROKER)),
                request -> "ok")).doesNotThrowAnyException();
    }

    private BrokerCallerIdentityInterceptor workloadInterceptor() {
        return new BrokerCallerIdentityInterceptor(Map.of(
                BrokerCallerRole.GATEWAY, GATEWAY_PREFIX,
                BrokerCallerRole.BROKER, BROKER_PREFIX,
                BrokerCallerRole.MESSAGE, "spiffe://im-chat/message/",
                BrokerCallerRole.ACCOUNT, "spiffe://im-chat/account/",
                BrokerCallerRole.OPS, "spiffe://im-chat/im-ops/"), Set.of());
    }

    private BoltRequestContext context(String identity) {
        return new BoltRequestContext("remote", "127.0.0.1", identity, false, Set.of());
    }

    private static BrokerClusterState state() {
        return new BrokerClusterState(1L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(node("broker-a"), node("broker-b")));
    }

    private static BrokerNode node(String brokerId) {
        return new BrokerNode(brokerId, "127.0.0.1:18020",
                BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE);
    }

    private record FixedRepository(BrokerClusterState getCommittedState)
            implements BrokerClusterConsensusStub {
        @Override
        public Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId) {
            return Optional.empty();
        }

        @Override
        public CompletionStage<BrokerClusterConsensusResult> submitCommand(BrokerClusterConsensusCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
