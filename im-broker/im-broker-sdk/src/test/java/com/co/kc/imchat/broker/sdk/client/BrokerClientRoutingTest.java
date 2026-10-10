package com.co.kc.imchat.broker.sdk.client;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerStatus;
import com.co.kc.imchat.broker.sdk.routing.RouteBrokerSelectors;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000);
        long userId = 100L;
        String expected = RouteBrokerSelectors.forAlgorithm(state.algorithm())
                .select(userId, state).routeEndpoint();

        client.refreshClusterState();
        client.registerConnection(new ConnectionRegisterParams(userId, "gateway-a", 1L));

        assertThat(invoker.addresses).containsExactly("10.0.0.9:18020", expected);
        assertThat(invoker.operations).containsExactly(
                "getClusterState", "registerConnection");
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
        assertThat(invoker.routeRequests.get(0).routeContext().deadlineEpochMillis())
                .isEqualTo(invoker.routeRequests.get(1).routeContext().deadlineEpochMillis());
        assertThat(invoker.routeRequests.get(0).routeContext().routingEpoch()).isEqualTo(3L);
        assertThat(invoker.routeRequests.get(1).routeContext().routingEpoch()).isEqualTo(4L);
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

    private BrokerClient client(CapturingInvoker invoker) {
        return new BrokerClient(invoker,
                new FixedDiscoveryClient(List.of(
                        new FixedServiceInstance("bootstrap", "10.0.0.9", 18020))),
                ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000);
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

    private static final class CapturingInvoker implements BoltInvoker {

        private final BrokerClusterState state;
        private final List<BrokerClusterState> clusterStates = new ArrayList<>();
        private final List<RouteBrokerResult> routeResults = new ArrayList<>();
        private final List<String> addresses = new ArrayList<>();
        private final List<String> operations = new ArrayList<>();
        private final List<ConnectionRegisterParams> routeRequests = new ArrayList<>();
        private boolean failRouteCall;

        private CapturingInvoker(BrokerClusterState state) {
            this.state = state;
            this.clusterStates.add(state);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, R> R invoke(String address, String service, String operation,
                               T request, Class<R> responseType, int timeoutMillis) {
            addresses.add(address);
            operations.add(operation);
            if (operation.equals("getClusterState")) {
                return (R) (clusterStates.isEmpty() ? state : clusterStates.remove(0));
            }
            if (operation.equals("registerConnection")) {
                routeRequests.add((ConnectionRegisterParams) request);
                if (failRouteCall) {
                    throw new IllegalStateException("network timeout; outcome unknown");
                }
                return (R) (routeResults.isEmpty()
                        ? RouteBrokerResult.executed(null) : routeResults.remove(0));
            }
            return null;
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
