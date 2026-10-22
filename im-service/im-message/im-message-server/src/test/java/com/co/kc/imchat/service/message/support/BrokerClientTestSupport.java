package com.co.kc.imchat.service.message.support;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerResult;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

public final class BrokerClientTestSupport {
    private BrokerClientTestSupport() {
    }

    public static BrokerClient client(BoltInvoker invoker) {
        return new BrokerClient(routingInvoker(invoker), discovery(), ServiceName.IM_BROKER,
                BrokerLoadBalance.HASH, 3000);
    }

    private static BoltInvoker routingInvoker(BoltInvoker delegate) {
        return new BoltInvoker() {
            @Override
            public <T, R> R invokeAuthenticated(String address, String service, String operation, T request,
                                                Class<R> responseType, int timeoutMillis,
                                                String expectedPeerIdentity, String identityPrefix) {
                throw new UnsupportedOperationException("authenticated invocation is not used by this fixture");
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T, R> R invoke(String address, String service, String operation, T request,
                                   Class<R> responseType, int timeoutMillis) {
                if ("getClusterState".equals(operation)) {
                    return (R) state();
                }
                BrokerFrameWriteResult result = delegate.invoke(address, service, operation,
                        request, BrokerFrameWriteResult.class, timeoutMillis);
                return (R) RouteBrokerResult.executed(1L, result);
            }
        };
    }

    private static BrokerClusterState state() {
        return new BrokerClusterState(1L, 1L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("broker-test", "127.0.0.1:12200",
                        BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
    }

    public static DiscoveryClient discovery() {
        return new DiscoveryClient() {
            private final ServiceInstance instance = new Instance();

            @Override
            public List<ServiceInstance> getInstances(String serviceId) {
                return List.of(instance);
            }

            @Override
            public List<String> getServices() {
                return List.of(ServiceName.IM_BROKER.value());
            }

            @Override
            public String description() {
                return "test discovery";
            }
        };
    }

    private record Instance() implements ServiceInstance {
        @Override public String getInstanceId() { return "broker-test"; }
        @Override public String getServiceId() { return ServiceName.IM_BROKER.value(); }
        @Override public String getHost() { return "127.0.0.1"; }
        @Override public int getPort() { return 12200; }
        @Override public boolean isSecure() { return false; }
        @Override public URI getUri() { return URI.create("http://127.0.0.1:12200"); }
        @Override public String getScheme() { return "http"; }
        @Override public Map<String, String> getMetadata() { return Map.of(); }
    }
}
