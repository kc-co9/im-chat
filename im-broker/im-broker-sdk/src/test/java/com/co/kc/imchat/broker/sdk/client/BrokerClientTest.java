package com.co.kc.imchat.broker.sdk.client;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltService;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.params.GatewayHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionSyncParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.model.result.BrokerListResult;
import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerClientTest {

    @Test
    void invokesBrokerOperationsThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        invoker.response = BrokerFrameWriteResult.ok();
        BrokerClient client = new BrokerClient(
                invoker, "127.0.0.1:12200", 3000);
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
        BrokerClient client = new BrokerClient(
                invoker, "127.0.0.1:12200,127.0.0.1:12202", BrokerLoadBalance.ROUND_ROBIN, 3000);
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
        BrokerClient client = new BrokerClient(
                invoker, " 127.0.0.1:12200, ,127.0.0.1:12202 ", BrokerLoadBalance.ROUND_ROBIN, 3000);
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
        BrokerClient client = new BrokerClient(
                invoker, "127.0.0.1:12200,127.0.0.1:12202", BrokerLoadBalance.HASH, 3000);

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
        BrokerClient client = new BrokerClient(
                invoker, "127.0.0.1:12200,127.0.0.1:12202", BrokerLoadBalance.HASH, 3000);
        FrameRequest request = new FrameRequest("1", "message.private.send", "1", "trace", Map.of());

        client.writeFrame(BrokerFrameWriteParams.inbound(100L, "conn-1", request));
        client.writeFrame(BrokerFrameWriteParams.inbound(100L, "conn-2", request));

        assertThat(invoker.addresses).hasSize(2);
        assertThat(invoker.addresses).containsOnly(invoker.addresses.get(0));
    }

    @Test
    void hashesConnectionRegisterAndUnregisterByUserId() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = new BrokerClient(
                invoker, "127.0.0.1:12200,127.0.0.1:12202", BrokerLoadBalance.HASH, 3000);

        client.registerConnection(new ConnectionRegisterParams(100L, "gw-1"));
        client.unregisterConnection(new ConnectionUnregisterParams(100L, "gw-2"));

        assertThat(invoker.addresses).hasSize(2);
        assertThat(invoker.addresses).containsOnly(invoker.addresses.get(0));
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
        BrokerClient client = new BrokerClient(invoker, "127.0.0.1:12200", 3000);
        GatewayRegisterParams command = new GatewayRegisterParams("gw-1", "10.0.0.8", 12201);

        client.registerGateway(command);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.GATEWAY.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.REGISTER_GATEWAY.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void registersBrokerThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = new BrokerClient(invoker, "127.0.0.1:12200", 3000);
        BrokerRegisterParams command = new BrokerRegisterParams("broker-1", "127.0.0.1", 12200);

        client.registerBroker(command);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.BROKER.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.REGISTER_BROKER.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void heartbeatsBrokerThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = new BrokerClient(invoker, "127.0.0.1:12200", 3000);
        BrokerHeartbeatParams command = new BrokerHeartbeatParams("broker-1");

        client.heartbeatBroker(command);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.BROKER.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.HEARTBEAT_BROKER.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void unregistersBrokerThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = new BrokerClient(invoker, "127.0.0.1:12200", 3000);
        BrokerUnregisterParams command = new BrokerUnregisterParams("broker-1");

        client.unregisterBroker(command);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.BROKER.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.UNREGISTER_BROKER.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void unregistersGatewayThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = new BrokerClient(invoker, "127.0.0.1:12200", 3000);
        GatewayUnregisterParams command = new GatewayUnregisterParams("gw-1");

        client.unregisterGateway(command);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.GATEWAY.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.UNREGISTER_GATEWAY.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void heartbeatsGatewayThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = new BrokerClient(invoker, "127.0.0.1:12200", 3000);
        GatewayHeartbeatParams command = new GatewayHeartbeatParams("gw-1");

        client.heartbeatGateway(command);

        assertThat(invoker.service).isEqualTo(BrokerBoltService.GATEWAY.service());
        assertThat(invoker.operation).isEqualTo(BrokerBoltOperation.HEARTBEAT_GATEWAY.operation());
        assertThat(invoker.request).isSameAs(command);
    }

    @Test
    void syncsConnectionRoutesThroughBolt() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        BrokerClient client = new BrokerClient(invoker, "127.0.0.1:12200,127.0.0.1:12202", 3000);
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
        BrokerClient client = new BrokerClient(invoker, "127.0.0.1:12200,127.0.0.1:12202", 3000);

        client.syncConnections(new ConnectionSyncParams("gw-1", List.of(100L)));

        assertThat(invoker.addresses).containsExactly("127.0.0.1:12200", "127.0.0.1:12202");
    }

    @Test
    void refreshesBrokerAddressesByBrokerIdOrder() {
        CapturingBoltInvocation invoker = new CapturingBoltInvocation();
        invoker.response = new BrokerListResult(List.of(
                new BrokerEndpointDTO("broker-2", "127.0.0.1", 12202, Instant.now(), Instant.now()),
                new BrokerEndpointDTO("broker-1", "127.0.0.1", 12200, Instant.now(), Instant.now())));
        BrokerClient client = new BrokerClient(invoker, "127.0.0.1:12204", 3000);
        ConnectionSyncParams command = new ConnectionSyncParams("gw-1", List.of(100L));

        client.refreshBrokerAddresses();
        client.syncConnections(command);

        assertThat(invoker.addresses).containsExactly("127.0.0.1:12204", "127.0.0.1:12200", "127.0.0.1:12202");
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
}
