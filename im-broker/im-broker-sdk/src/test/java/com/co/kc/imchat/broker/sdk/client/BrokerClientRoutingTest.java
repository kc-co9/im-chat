package com.co.kc.imchat.broker.sdk.client;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotAckParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotParams;
import com.co.kc.imchat.broker.sdk.model.result.GatewayRouteSnapshotAckResult;
import com.co.kc.imchat.broker.sdk.lifecycle.BrokerRefresher;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerStatus;
import com.co.kc.imchat.broker.sdk.routing.RouteBrokerSelectors;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerClientRoutingTest {

    @Test
    void bootstrapsCommittedStateThenRoutesUserDirectlyToRouteBroker() {
        BrokerClusterState state = state();
        CapturingInvoker invoker = new CapturingInvoker(state);
        BrokerClient client = new BrokerClient(invoker,
                new FixedDiscoveryClient(List.of(
                        new FixedServiceInstance("bootstrap", "10.0.0.9", 18020))),
                ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000, List.of(new BoltTarget("10.0.0.9:18020", "spiffe://im-chat/broker/bootstrap")));
        long userId = 100L;
        String expected = RouteBrokerSelectors.forAlgorithm(state.algorithm())
                .select(userId, state).routeEndpoint();

        client.refreshClusterState();
        client.registerConnection(new ConnectionRegisterParams(userId, "gateway-a", 1L));

        assertThat(invoker.addresses).containsExactly("10.0.0.9:18020", expected);
        assertThat(invoker.targets).containsExactly(
                new BoltTarget("10.0.0.9:18020", "spiffe://im-chat/broker/bootstrap"),
                new BoltTarget(expected, "spiffe://im-chat/broker/" +
                        RouteBrokerSelectors.forAlgorithm(state.algorithm()).select(userId, state).brokerId()));
        assertThat(invoker.operations).containsExactly(
                "getClusterState", "registerConnection");
        assertThat(invoker.timeoutsMs).containsExactly(3000, 3000);
    }

    @Test
    void rejectsBootstrapInCustomBrokerNamespace() {
        assertThatThrownBy(() -> clientWithTargets(List.of(new BoltTarget("10.0.0.9:18020", "spiffe://custom.example/broker/bootstrap"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prefix");
    }

    @Test
    void refreshesAndRetriesOnceAfterPreSideEffectRejection() {
        BrokerClusterState first = state(7L, 3L,
                active("broker-a", "10.0.0.1:18020"),
                active("broker-b", "10.0.0.2:18020"));
        BrokerClusterState second = state(8L, 4L,
                active("broker-b", "10.0.0.2:18020"),
                active("broker-c", "10.0.0.3:18020"));
        CapturingInvoker invoker = new CapturingInvoker(first);
        invoker.clusterStates.add(second);
        invoker.routeResults.add(RouteBrokerResult.rejected(
                RouteBrokerStatus.ROUTE_MOVED, 4L, null));
        invoker.routeResults.add(RouteBrokerResult.executed(null));
        BrokerClient client = client(invoker);

        client.registerConnection(new ConnectionRegisterParams(100L, "gateway-a", 1L));

        assertThat(invoker.operations).containsExactly(
                "getClusterState", "registerConnection", "getClusterState", "registerConnection");
        assertThat(invoker.routeRequests).hasSize(2);
        assertThat(invoker.routeRequests.get(0).routeContext().operationId())
                .isEqualTo(invoker.routeRequests.get(1).routeContext().operationId());
        assertThat(invoker.routeRequests.get(0).routeContext().expireAtMs())
                .isEqualTo(invoker.routeRequests.get(1).routeContext().expireAtMs());
        assertThat(invoker.routeRequests.get(0).routeContext().routingEpoch()).isEqualTo(3L);
        assertThat(invoker.routeRequests.get(1).routeContext().routingEpoch()).isEqualTo(4L);
        assertThat(invoker.targets.get(1)).isEqualTo(new BoltTarget(RouteBrokerSelectors.forAlgorithm(first.algorithm()).select(100L, first).routeEndpoint(), "spiffe://im-chat/broker/" + RouteBrokerSelectors.forAlgorithm(first.algorithm()).select(100L, first).brokerId()));
        assertThat(invoker.targets.get(3)).isEqualTo(new BoltTarget(RouteBrokerSelectors.forAlgorithm(second.algorithm()).select(100L, second).routeEndpoint(), "spiffe://im-chat/broker/" + RouteBrokerSelectors.forAlgorithm(second.algorithm()).select(100L, second).brokerId()));
    }

    @Test
    void doesNotReplayWhenRpcOutcomeIsUnknown() {
        CapturingInvoker invoker = new CapturingInvoker(state());
        invoker.failRouteCall = true;
        BrokerClient client = client(invoker);

        assertThatThrownBy(() -> client.registerConnection(
                new ConnectionRegisterParams(100L, "gateway-a", 1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outcome unknown");

        assertThat(invoker.operations).containsExactly("getClusterState", "registerConnection");
    }

    @Test
    void retriesEachExplicitPreSideEffectRejectionAtMostOnce() {
        BrokerClusterState state = state();
        String expectedEndpoint = RouteBrokerSelectors.forAlgorithm(state.algorithm())
                .select(100L, state).routeEndpoint();
        List<RouteBrokerStatus> statuses = List.of(
                RouteBrokerStatus.ROUTE_MOVED,
                RouteBrokerStatus.BROKER_STATE_STALE,
                RouteBrokerStatus.ROUTE_MISMATCH,
                RouteBrokerStatus.ROUTING_FENCED);

        for (RouteBrokerStatus status : statuses) {
            CapturingInvoker invoker = new CapturingInvoker(state);
            invoker.clusterStates.add(state);
            invoker.routeResults.add(RouteBrokerResult.rejected(
                    status, state.epoch(), expectedEndpoint));
            invoker.routeResults.add(RouteBrokerResult.executed(null));
            BrokerClient client = client(invoker);

            client.registerConnection(new ConnectionRegisterParams(100L, "gateway-a", 1L));

            assertThat(invoker.operations)
                    .as("operation sequence for %s", status)
                    .containsExactly("getClusterState", "registerConnection",
                            "getClusterState", "registerConnection");
        }
    }

    @Test
    void stopsWhenSameEpochMismatchDisagreesWithLocalSelector() {
        BrokerClusterState state = state();
        CapturingInvoker invoker = new CapturingInvoker(state);
        invoker.clusterStates.add(state);
        invoker.routeResults.add(RouteBrokerResult.rejected(
                RouteBrokerStatus.ROUTE_MISMATCH, state.epoch(), "10.0.0.99:18020"));
        BrokerClient client = client(invoker);

        assertThatThrownBy(() -> client.registerConnection(
                new ConnectionRegisterParams(100L, "gateway-a", 1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("selector mismatch");

        assertThat(invoker.operations).containsExactly(
                "getClusterState", "registerConnection", "getClusterState");
    }

    @Test
    void bootstrapRequiresDeploymentPairsAndIgnoresUntrustedDiscoveryMetadata() {
        CapturingInvoker invoker = new CapturingInvoker(state());
        assertThatThrownBy(() -> new BrokerClient(invoker,
                new FixedDiscoveryClient(List.of(new FixedServiceInstance("self-claimed-broker", "10.0.0.99", 18020))),
                ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000, identities()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("trusted bootstrap identity pair");
        assertThat(invoker.targets).isEmpty();
        assertThatThrownBy(() -> clientWithTargets(List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pairs");
        assertThatThrownBy(() -> clientWithTargets(List.of(
                new BoltTarget("10.0.0.9:18020", "spiffe://im-chat/broker/bootstrap"),
                new BoltTarget("10.0.0.9:18020", "spiffe://im-chat/broker/conflicting"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Conflicting");
        assertThatThrownBy(() -> clientWithTargets(List.of(
                new BoltTarget("10.0.0.9:18020", "spiffe://foreign/broker/bootstrap"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("prefix");
        BrokerClient mixed = new BrokerClient(invoker, new FixedDiscoveryClient(List.of(
                new FixedServiceInstance("untrusted", "10.0.0.99", 18020),
                new FixedServiceInstance("untrusted-label", "10.0.0.9", 18020))),
                ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000, identities());
        mixed.refreshClusterState();
        assertThat(invoker.targets).containsExactly(new BoltTarget("10.0.0.9:18020", "spiffe://im-chat/broker/bootstrap"));
    }

    @Test
    void snapshotAndAckAttemptsPreserveCommittedIdentityAndAuthenticationFailureStopsTraversal() {
        CapturingInvoker invoker = new CapturingInvoker(state());
        BrokerClient client = client(invoker);
        client.refreshClusterState();
        client.applyGatewayRouteSnapshot(new GatewayRouteSnapshotParams("gateway-a", 3L, 1L, "broker-b",
                "batch", 0, 1, "checksum", Long.MAX_VALUE, List.of(100L)));
        invoker.ackResults.add(GatewayRouteSnapshotAckResult.notLeader("untrusted-hint:1"));
        invoker.ackResults.add(GatewayRouteSnapshotAckResult.acknowledged());
        assertThat(client.acknowledgeGatewayRouteSnapshot(new GatewayRouteSnapshotAckParams(
                "gateway-a", 3L, 1L, 1L, "checksum", Long.MAX_VALUE)).status())
                .isEqualTo(GatewayRouteSnapshotAckResult.Status.ACKNOWLEDGED);
        assertThat(invoker.targets).containsExactly(
                new BoltTarget("10.0.0.9:18020", "spiffe://im-chat/broker/bootstrap"),
                new BoltTarget("10.0.0.2:18020", "spiffe://im-chat/broker/broker-b"),
                new BoltTarget("10.0.0.1:18020", "spiffe://im-chat/broker/broker-a"),
                new BoltTarget("10.0.0.2:18020", "spiffe://im-chat/broker/broker-b"));
        BoltInvocationException refused = new BoltInvocationException(BoltInvocationException.Failure.AUTHENTICATION,
                "exact peer refused", new javax.net.ssl.SSLException("wrong identity"));
        invoker.failure = refused;
        int previous = invoker.targets.size();
        assertThatThrownBy(() -> client.acknowledgeGatewayRouteSnapshot(new GatewayRouteSnapshotAckParams(
                "gateway-a", 3L, 1L, 1L, "checksum", Long.MAX_VALUE))).isSameAs(refused);
        assertThat(invoker.targets).hasSize(previous + 1);
        assertThatThrownBy(() -> new BrokerRefresher(client).refresh()).isSameAs(refused);
        assertThat(invoker.targets).hasSize(previous + 2);
    }

    @Test
    void staleRefreshCannotReplaceCurrentCommittedTargetBindings() {
        BrokerClusterState newest = state(9L, 5L, active("broker-current", "10.0.0.3:18020"));
        CapturingInvoker invoker = new CapturingInvoker(newest);
        BrokerClient client = client(invoker);
        client.refreshClusterState();
        invoker.clusterStates.add(state());
        client.refreshClusterState();
        client.refreshClusterState();
        assertThat(client.currentClusterState()).isEqualTo(newest);
        assertThat(invoker.targets.subList(1, 3)).containsOnly(new BoltTarget("10.0.0.3:18020", "spiffe://im-chat/broker/broker-current"));
    }

    @Test
    void everyUserRouteOperationKeepsTheSelectedCommittedNode() {
        BrokerClusterState state = state();
        CapturingInvoker invoker = new CapturingInvoker(state);
        BrokerClient client = client(invoker);
        client.registerConnection(new ConnectionRegisterParams(100L, "gateway-a", 1L));
        client.unregisterConnection(new ConnectionUnregisterParams(100L, "gateway-a", 1L));
        client.closeConnections("close-stable", new ConnectionCloseParams(100L, "session-v1"));
        client.writeFrame("write-stable", BrokerFrameWriteParams.outbound(100L, null));
        BrokerNode selected = RouteBrokerSelectors.forAlgorithm(state.algorithm()).select(100L, state);
        assertThat(invoker.targets.subList(1, 5)).containsOnly(
                new BoltTarget(selected.routeEndpoint(), "spiffe://im-chat/broker/" + selected.brokerId()));
        assertThat(invoker.operations).containsExactly("getClusterState", "registerConnection", "unregisterConnection", "closeConnections", "writeFrame");
    }

    @Test
    void routeAuthenticationFailureDoesNotRefreshOrReplayOnAnotherNode() {
        CapturingInvoker invoker = new CapturingInvoker(state());
        BrokerClient client = client(invoker);
        client.refreshClusterState();
        BoltInvocationException refusal = new BoltInvocationException(BoltInvocationException.Failure.AUTHENTICATION,
                "wrong committed peer", new javax.net.ssl.SSLException("wrong peer URI"));
        invoker.failure = refusal;
        assertThatThrownBy(() -> client.registerConnection(new ConnectionRegisterParams(100L, "gateway-a", 1L)))
                .isSameAs(refusal);
        assertThat(invoker.operations).containsExactly("getClusterState", "registerConnection");
    }

    private BrokerClient client(CapturingInvoker invoker) {
        return new BrokerClient(invoker,
                new FixedDiscoveryClient(List.of(
                        new FixedServiceInstance("bootstrap", "10.0.0.9", 18020))),
                ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000, identities());
    }

    private BrokerClusterState state() {
        return state(7L, 3L,
                active("broker-a", "10.0.0.1:18020"),
                active("broker-b", "10.0.0.2:18020"));
    }

    private BrokerClusterState state(long revision, long epoch, BrokerNode... brokers) {
        return new BrokerClusterState(revision, epoch,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(brokers));
    }

    private BrokerNode active(String brokerId, String endpoint) {
        return new BrokerNode(brokerId, endpoint,
                BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE);
    }

    @Test
    void rejectsNullAndConflictingPairsBeforeDiscovery() {
        DiscoveryClient unusedDiscovery = org.mockito.Mockito.mock(DiscoveryClient.class);
        CapturingInvoker invoker = new CapturingInvoker(state());
        assertThatThrownBy(() -> new BrokerClient(invoker, unusedDiscovery,
                ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pairs");
        assertThatThrownBy(() -> new BrokerClient(invoker, unusedDiscovery,
                ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000, List.of(
                        new BoltTarget("10.0.0.9:18020", "spiffe://im-chat/broker/bootstrap"),
                        new BoltTarget("10.0.0.8:18020", "spiffe://im-chat/broker/bootstrap"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Conflicting");
        org.mockito.Mockito.verifyNoInteractions(unusedDiscovery);
    }

    @Test
    void snapshotsMutableBootstrapPairsAndDeduplicatesIdenticalBindings() {
        CapturingInvoker invoker = new CapturingInvoker(state());
        ArrayList<BoltTarget> pairs = new ArrayList<>(identities());
        pairs.add(pairs.getFirst());
        BrokerClient client = new BrokerClient(invoker,
                new FixedDiscoveryClient(List.of(new FixedServiceInstance("bootstrap", "10.0.0.9", 18020))),
                ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000, pairs);
        pairs.clear();
        client.refreshClusterState();
        assertThat(invoker.targets).containsExactly(
                new BoltTarget("10.0.0.9:18020", "spiffe://im-chat/broker/bootstrap"));
    }

    private BrokerClient clientWithTargets(List<BoltTarget> targets) {
        return new BrokerClient(new CapturingInvoker(state()),
                new FixedDiscoveryClient(List.of(new FixedServiceInstance("bootstrap", "10.0.0.9", 18020))),
                ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000, targets);
    }

    private List<BoltTarget> identities() {
        return List.of(new BoltTarget("10.0.0.9:18020", "spiffe://im-chat/broker/bootstrap"));
    }

    private static final class CapturingInvoker implements BoltInvoker {
        private final List<BoltTarget> targets = new ArrayList<>();
        @Override
        @SuppressWarnings("unchecked")
        public <T, R> R invoke(BoltTarget target, String service, String operation,
                               T request, Class<R> responseType, int timeoutMillis) {
            targets.add(target);

            String address = target.address();

            addresses.add(address);
            operations.add(operation);
            timeoutsMs.add(timeoutMillis);
            if (failure != null) { throw failure; }
            if (operation.equals("ackGatewayRouteSnapshot")) { return (R) ackResults.removeFirst(); }
            if (operation.equals("getClusterState")) {
                return (R) (clusterStates.isEmpty() ? state : clusterStates.remove(0));
            }
            if (List.of("registerConnection", "unregisterConnection", "closeConnections", "writeFrame").contains(operation)) {
                if (request instanceof ConnectionRegisterParams registration) { routeRequests.add(registration); }
                if (failRouteCall) {
                    throw new IllegalStateException("network timeout; outcome unknown");
                }
                return (R) (routeResults.isEmpty()
                        ? RouteBrokerResult.executed(null) : routeResults.remove(0));
            }
            return null;
        }

        private final BrokerClusterState state;
        private final List<BrokerClusterState> clusterStates = new ArrayList<>();
        private final List<RouteBrokerResult> routeResults = new ArrayList<>();
        private final List<String> addresses = new ArrayList<>();
        private final List<String> operations = new ArrayList<>();
        private final List<Integer> timeoutsMs = new ArrayList<>();
        private final List<ConnectionRegisterParams> routeRequests = new ArrayList<>();
        private boolean failRouteCall;
        private BoltInvocationException failure;
        private final List<GatewayRouteSnapshotAckResult> ackResults = new ArrayList<>();

        private CapturingInvoker(BrokerClusterState state) {
            this.state = state;
            this.clusterStates.add(state);
        }

    }

    private record FixedDiscoveryClient(List<ServiceInstance> instances)
            implements DiscoveryClient {
        @Override
        public List<ServiceInstance> getInstances(String serviceId) {
            return instances;
        }

        @Override
        public List<String> getServices() {
            return List.of(ServiceName.IM_BROKER.value());
        }

        @Override
        public String description() {
            return "fixed";
        }
    }

    private record FixedServiceInstance(String instanceId, String host, int port)
            implements ServiceInstance {
        @Override
        public String getInstanceId() {
            return instanceId;
        }

        @Override
        public String getServiceId() {
            return ServiceName.IM_BROKER.value();
        }

        @Override
        public String getHost() {
            return host;
        }

        @Override
        public int getPort() {
            return port;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public URI getUri() {
            return URI.create("bolt://" + host + ":" + port);
        }

        @Override
        public String getScheme() {
            return "bolt";
        }

        @Override
        public Map<String, String> getMetadata() {
            return Map.of();
        }
    }
}
