package com.co.kc.imchat.broker.sdk.client;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltService;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.params.GatewayHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionSyncParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.model.result.BrokerListResult;
import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerClientTest {

    @Test
    void createsClientFromDiscoveredBrokerService() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        invoker.response = BrokerFrameWriteResult.ok();
        DiscoveryClient discoveryClient = new StaticDiscoveryClient(List.of(
                new StaticServiceInstance("broker-1", "10.0.0.1", 12200),
                new StaticServiceInstance("broker-2", "10.0.0.2", 12200)));

        BrokerClient client = new BrokerClient(
                invoker, discoveryClient, ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000);
        client.writeFrame(BrokerFrameWriteParams.inbound(1L, "conn-1",
                new FrameRequest("1", "message.private.send", "1", "trace", Map.of())));

        assertThat(invoker.address).isIn("10.0.0.1:12200", "10.0.0.2:12200");
    }

    @Test
    void invokesBrokerOperationsThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        invoker.response = BrokerFrameWriteResult.ok();
        BrokerClient client = client(invoker, "127.0.0.1:12200", BrokerLoadBalance.HASH);
        BrokerFrameWriteParams command = BrokerFrameWriteParams.inbound(1L, "conn-1",
                new FrameRequest("1", "message.private.send", "1", "trace", Map.of()));

        BrokerFrameWriteResult response = client.writeFrame(command);

        assertThat(response.processed()).isTrue();
        assertThat(invoker.address).isEqualTo("127.0.0.1:12200");
        assertThat(invoker.service).isEqualTo(BrokerBoltService.FRAME.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.WRITE_FRAME.operation());
        assertThat(invoker.request).isSameAs(command);
        assertThat(invoker.responseType).isEqualTo(BrokerFrameWriteResult.class);
    }

    @Test
    void loadBalancesBrokerAddressesByRoundRobin() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        invoker.response = BrokerFrameWriteResult.ok();
        BrokerClient client = client(invoker, "127.0.0.1:12200,127.0.0.1:12202", BrokerLoadBalance.ROUND_ROBIN);
        BrokerFrameWriteParams command = BrokerFrameWriteParams.inbound(1L, "conn-1",
                new FrameRequest("1", "message.private.send", "1", "trace", Map.of()));

        client.writeFrame(command);
        client.writeFrame(command);
        client.writeFrame(command);

        assertThat(invoker.addresses)
                .containsExactly("127.0.0.1:12200", "127.0.0.1:12202", "127.0.0.1:12200");
    }

    @Test
    void ignoresBlankBrokerAddresses() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        invoker.response = BrokerFrameWriteResult.ok();
        BrokerClient client = client(invoker, " 127.0.0.1:12200, ,127.0.0.1:12202 ", BrokerLoadBalance.ROUND_ROBIN);
        BrokerFrameWriteParams command = BrokerFrameWriteParams.inbound(1L, "conn-1",
                new FrameRequest("1", "message.private.send", "1", "trace", Map.of()));

        client.writeFrame(command);
        client.writeFrame(command);

        assertThat(invoker.addresses)
                .containsExactly("127.0.0.1:12200", "127.0.0.1:12202");
    }

    @Test
    void hashesGatewayOperationsByGatewayId() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = client(invoker, "127.0.0.1:12200,127.0.0.1:12202", BrokerLoadBalance.HASH);

        client.registerGateway(new GatewayRegisterParams("gw-1", "127.0.0.1", 12201));
        client.heartbeatGateway(new GatewayHeartbeatParams("gw-1"));
        client.unregisterGateway(new GatewayUnregisterParams("gw-1"));

        assertThat(invoker.addresses).hasSize(3);
        assertThat(invoker.addresses).containsOnly(invoker.addresses.get(0));
    }

    @Test
    void hashesFrameWriteByUserId() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        invoker.response = BrokerFrameWriteResult.ok();
        BrokerClient client = client(invoker, "127.0.0.1:12200,127.0.0.1:12202", BrokerLoadBalance.HASH);
        FrameRequest request = new FrameRequest("1", "message.private.send", "1", "trace", Map.of());

        client.writeFrame(BrokerFrameWriteParams.inbound(100L, "conn-1", request));
        client.writeFrame(BrokerFrameWriteParams.inbound(100L, "conn-2", request));

        assertThat(invoker.addresses).hasSize(2);
        assertThat(invoker.addresses).containsOnly(invoker.addresses.get(0));
    }

    @Test
    void hashesConnectionRegisterAndUnregisterByUserId() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = client(invoker, "127.0.0.1:12200,127.0.0.1:12202", BrokerLoadBalance.HASH);

        client.registerConnection(new ConnectionRegisterParams(100L, "gw-1"));
        client.unregisterConnection(new ConnectionUnregisterParams(100L, "gw-2"));

        assertThat(invoker.addresses).hasSize(2);
        assertThat(invoker.addresses).containsOnly(invoker.addresses.get(0));
    }

    @Test
    void routesConnectionCloseControlByUserId() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = client(invoker, "127.0.0.1:12200", BrokerLoadBalance.HASH);
        ConnectionCloseParams params = new ConnectionCloseParams(100L, "session-old");

        client.closeConnections(params);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.CONNECTION.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.CLOSE_CONNECTIONS.operation());
        assertThat(invoker.request).isSameAs(params);
    }

    @Test
    void serializesWriteResultAsConnectionStatusList() {
        BrokerFrameWriteResult response = BrokerFrameWriteResult.writeResult(1L, List.of("conn-1"), List.of("conn-2"));

        String json = JsonUtils.toJson(response);

        assertThat(json).contains("\"writeList\"");
        assertThat(json).contains("\"connectionId\":\"conn-1\"", "\"status\":\"ACCEPTED\"");
        assertThat(json).contains("\"connectionId\":\"conn-2\"", "\"status\":\"FAILED\"");
        assertThat(json).doesNotContain("acceptedConnectionIds", "failedConnectionIds");
    }

    @Test
    void registersGatewayThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = client(invoker, "127.0.0.1:12200", BrokerLoadBalance.HASH);
        GatewayRegisterParams command = new GatewayRegisterParams("gw-1", "10.0.0.8", 12201);

        client.registerGateway(command);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.GATEWAY.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.REGISTER_GATEWAY.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void registersBrokerThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = client(invoker, "127.0.0.1:12200", BrokerLoadBalance.HASH);
        BrokerRegisterParams command = new BrokerRegisterParams("broker-1", "127.0.0.1", 12200);

        client.registerBroker(command);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.BROKER.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.REGISTER_BROKER.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void heartbeatsBrokerThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = client(invoker, "127.0.0.1:12200", BrokerLoadBalance.HASH);
        BrokerHeartbeatParams command = new BrokerHeartbeatParams("broker-1");

        client.heartbeatBroker(command);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.BROKER.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.HEARTBEAT_BROKER.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void unregistersBrokerThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = client(invoker, "127.0.0.1:12200", BrokerLoadBalance.HASH);
        BrokerUnregisterParams command = new BrokerUnregisterParams("broker-1");

        client.unregisterBroker(command);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.BROKER.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.UNREGISTER_BROKER.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void unregistersGatewayThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = client(invoker, "127.0.0.1:12200", BrokerLoadBalance.HASH);
        GatewayUnregisterParams command = new GatewayUnregisterParams("gw-1");

        client.unregisterGateway(command);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.GATEWAY.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.UNREGISTER_GATEWAY.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void heartbeatsGatewayThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = client(invoker, "127.0.0.1:12200", BrokerLoadBalance.HASH);
        GatewayHeartbeatParams command = new GatewayHeartbeatParams("gw-1");

        client.heartbeatGateway(command);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.GATEWAY.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.HEARTBEAT_GATEWAY.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void syncsConnectionRoutesThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = client(invoker, "127.0.0.1:12200,127.0.0.1:12202", BrokerLoadBalance.HASH);
        ConnectionSyncParams command = new ConnectionSyncParams("gw-1", List.of(100L));

        client.syncConnections(command);

        assertThat(invoker.addresses).containsExactly("127.0.0.1:12200", "127.0.0.1:12202");
        assertThat(invoker.service).isEqualTo(BrokerBoltService.CONNECTION.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.SYNC_CONNECTIONS.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void continuesSyncingConnectionRoutesWhenOneBrokerFails() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation() {
            @Override
            public <T, R> R invoke(String address, String service, String operation, T request,
                                   Class<R> responseType, int timeoutMillis) {
                super.invoke(address, service, operation, request, responseType, timeoutMillis);
                if ("127.0.0.1:12200".equals(address)) {
                    throw new IllegalStateException("broker unavailable");
                }
                return null;
            }
        };
        BrokerClient client = client(invoker, "127.0.0.1:12200,127.0.0.1:12202", BrokerLoadBalance.HASH);

        client.syncConnections(new ConnectionSyncParams("gw-1", List.of(100L)));

        assertThat(invoker.addresses).containsExactly("127.0.0.1:12200", "127.0.0.1:12202");
    }

    @Test
    void refreshesBrokerAddressesByBrokerIdOrder() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        invoker.response = new BrokerListResult(List.of(
                new BrokerEndpointDTO("broker-2", "127.0.0.1", 12202, Instant.now(), Instant.now()),
                new BrokerEndpointDTO("broker-1", "127.0.0.1", 12200, Instant.now(), Instant.now())));
        BrokerClient client = client(invoker, "127.0.0.1:12204", BrokerLoadBalance.HASH);
        ConnectionSyncParams command = new ConnectionSyncParams("gw-1", List.of(100L));

        client.refreshBrokerAddresses();
        client.syncConnections(command);

        assertThat(invoker.addresses).containsExactly("127.0.0.1:12204", "127.0.0.1:12200", "127.0.0.1:12202");
    }

    private static BrokerClient client(CapturingBoltInvocation invoker,
                                       String addresses,
                                       BrokerLoadBalance loadBalance) {
        List<ServiceInstance> instances = java.util.Arrays.stream(addresses.split(","))
                .map(String::trim)
                .filter(address -> !address.isBlank())
                .<ServiceInstance>map(address -> {
                    String[] parts = address.split(":");
                    return new StaticServiceInstance(address, parts[0], Integer.parseInt(parts[1]));
                })
                .toList();
        return new BrokerClient(invoker, new StaticDiscoveryClient(instances),
                ServiceName.IM_BROKER, loadBalance, 3000);
    }

    private static class CapturingBoltInvocation implements BoltInvoker {
        private String address;
        private final List<String> addresses = new ArrayList<>();
        private String service;
        private String operation;
        private Object request;
        private Class<?> responseType;
        private Object response;

        @Override
        @SuppressWarnings("unchecked")
        public <T, R> R invoke(String address, String service, String operation, T request,
                               Class<R> responseType, int timeoutMillis) {
            this.address = address;
            this.addresses.add(address);
            this.service = service;
            this.operation = operation;
            this.request = request;
            this.responseType = responseType;
            return (R) response;
        }
    }

    private record StaticServiceInstance(String instanceId, String host, int port) implements ServiceInstance {
        @Override
        public String getInstanceId() {
            return instanceId;
        }

        @Override
        public String getServiceId() {
            return "im-broker";
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
        public java.net.URI getUri() {
            return java.net.URI.create("bolt://" + host + ":" + port);
        }

        @Override
        public String getScheme() {
            return "bolt";
        }

        @Override
        public java.util.Map<String, String> getMetadata() {
            return Map.of();
        }
    }

    private record StaticDiscoveryClient(List<ServiceInstance> instances) implements DiscoveryClient {
        @Override
        public List<ServiceInstance> getInstances(String serviceId) {
            return instances;
        }

        @Override
        public List<String> getServices() {
            return List.of("im-broker");
        }

        @Override
        public String description() {
            return "static";
        }
    }
}
