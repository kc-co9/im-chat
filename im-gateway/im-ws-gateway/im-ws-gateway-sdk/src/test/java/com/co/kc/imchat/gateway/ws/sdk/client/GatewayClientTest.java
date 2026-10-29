package com.co.kc.imchat.gateway.ws.sdk.client;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.gateway.ws.sdk.model.dto.GatewayFrameWriteDTO;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltOperation;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltService;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayClientTest {

    @Test
    void invokesRegisteredGatewayAddressThroughBolt() {
        RecordingBoltInvoker invoker = new RecordingBoltInvoker();
        invoker.response = new GatewayFrameWriteResult(List.of("conn-1"), List.of());
        GatewayClient adapter = new GatewayClient(invoker, 3000,
                BoltTransportMode.MTLS, List.of());
        GatewayEndpointDTO gateway = new GatewayEndpointDTO("gw-1", "10.0.0.8", 12201,
                "spiffe://im-chat/gateway/test", Instant.now(), Instant.now());
        GatewayFrameWriteParams request = new GatewayFrameWriteParams(
                1L, "delivery-1", "attempt-1",
                new FrameResponse("1", FrameType.PUSH, "message.private.sent", "1", "trace",
                        null, null, Map.of()));

        GatewayFrameWriteResult response = adapter.writeFrame(gateway, request);

        assertThat(response.writeList()).containsExactly(GatewayFrameWriteDTO.accepted("conn-1"));
        assertThat(response.acceptedConnectionIds()).containsExactly("conn-1");
        assertThat(invoker.address).isEqualTo("10.0.0.8:12201");
        assertThat(invoker.typedTarget).isEqualTo(new BoltTarget(
                "10.0.0.8:12201", "spiffe://im-chat/gateway/test"));
        assertThat(invoker.service).isEqualTo(GatewayBoltService.FRAME.service());
        assertThat(invoker.operation).isEqualTo(GatewayBoltOperation.WRITE_FRAME.operation());
        assertThat(invoker.request).isSameAs(request);
        assertThat(invoker.responseType).isEqualTo(GatewayFrameWriteResult.class);
    }

    @Test
    void serializesWriteResultAsConnectionStatusList() {
        GatewayFrameWriteResult response = new GatewayFrameWriteResult(List.of("conn-1"), List.of("conn-2"));

        String json = JsonUtils.toJson(response);

        assertThat(json).contains("\"writeList\"");
        assertThat(json).contains("\"connectionId\":\"conn-1\"", "\"status\":\"ACCEPTED\"");
        assertThat(json).contains("\"connectionId\":\"conn-2\"", "\"status\":\"FAILED\"");
        assertThat(json).doesNotContain("acceptedConnectionIds", "failedConnectionIds");
    }

    @Test
    void sendsDedicatedConnectionCloseControl() {
        RecordingBoltInvoker invoker = new RecordingBoltInvoker();
        GatewayClient client = new GatewayClient(invoker, 3000,
                BoltTransportMode.MTLS, List.of());
        GatewayEndpointDTO gateway = new GatewayEndpointDTO(
                "gw-1", "10.0.0.8", 12201, "spiffe://im-chat/gateway/test",
                Instant.now(), Instant.now());
        ConnectionCloseParams params = new ConnectionCloseParams(
                "operation-1", 1L, "session-old");

        client.closeConnections(gateway, params);

        assertThat(invoker.typedTarget).isEqualTo(new BoltTarget("10.0.0.8:12201", "spiffe://im-chat/gateway/test"));
        assertThat(invoker.service).isEqualTo(GatewayBoltService.CONNECTION.service());
        assertThat(invoker.operation).isEqualTo(GatewayBoltOperation.CLOSE_CONNECTIONS.operation());
        assertThat(invoker.request).isSameAs(params);
        assertThat(invoker.responseType).isEqualTo(Void.class);
    }

    @Test
    void rejectsMissingOrForeignRegisteredIdentityWithoutDispatch() {
        RecordingBoltInvoker invoker = new RecordingBoltInvoker();
        GatewayClient client = new GatewayClient(invoker, 3000,
                BoltTransportMode.MTLS, List.of());
        for (String identity : java.util.Arrays.asList(null, "", "spiffe://im-chat/broker/fake", "spiffe://custom.example/gateway/fake", "spiffe://im-chat/gateway/")) {
            GatewayEndpointDTO gateway = new GatewayEndpointDTO("random-process-id", "10.0.0.8", 12201,
                    identity, Instant.now(), Instant.now());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.closeConnections(gateway,
                    new ConnectionCloseParams("operation", 1L, "session")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("registered workload identity");
        }
        assertThat(invoker.typedTarget).isNull();
        assertThat(invoker.address).isNull();
    }

    @Test
    void developmentRegisteredMarkerNeedsExplicitRoutingDescriptorForBothOperations() {
        RecordingBoltInvoker invoker = new RecordingBoltInvoker();
        java.util.ArrayList<BoltTarget> pairs = new java.util.ArrayList<>(List.of(new BoltTarget("127.0.0.1:12201", "spiffe://im-chat/gateway/dev")));
        GatewayClient client = new GatewayClient(invoker, 3000,
                BoltTransportMode.DEVELOPMENT_PLAINTEXT, pairs);
        pairs.clear();
        GatewayEndpointDTO gateway = new GatewayEndpointDTO("gw-dev", "127.0.0.1", 12201,
                "trusted-network://127.0.0.1", Instant.now(), Instant.now());
        client.closeConnections(gateway, new ConnectionCloseParams("close", 1L, "session"));
        assertThat(invoker.typedTarget).isEqualTo(new BoltTarget("127.0.0.1:12201", "spiffe://im-chat/gateway/dev"));
        client.writeFrame(gateway, new GatewayFrameWriteParams(1L, "delivery", "attempt", null));
        assertThat(invoker.typedTarget).isEqualTo(new BoltTarget("127.0.0.1:12201", "spiffe://im-chat/gateway/dev"));
        for (String marker : List.of("trusted-network://127.0.0.2", "trusted-network://user@127.0.0.1", "trusted-network://127.0.0.1/path", "spiffe://im-chat/gateway/dev")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.closeConnections(new GatewayEndpointDTO("gw", "127.0.0.1", 12201, marker, Instant.now(), Instant.now()), new ConnectionCloseParams("close", 1L, "session")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("marker");
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.closeConnections(new GatewayEndpointDTO("gw", "127.0.0.1", 12202, "trusted-network://127.0.0.1", Instant.now(), Instant.now()), new ConnectionCloseParams("close", 1L, "session")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no deployment pair");
    }

    @Test
    void configurationRejectsInvalidPairsAndMtlsNeverSelectsDevelopmentOverride() {
        String prefix = "spiffe://im-chat/gateway/";
        BoltTransportMode dev = BoltTransportMode.DEVELOPMENT_PLAINTEXT;
        BoltTarget pair = new BoltTarget("127.0.0.1:12201", prefix + "dev");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new GatewayClient(new RecordingBoltInvoker(), 3000, dev, List.of(pair, pair)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        for (String address : List.of("127.0.0.1:0", "127.0.0.1:65536", "127.0.0.1:1/path", "localhost:1", "10.0.0.1:1")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> new GatewayClient(new RecordingBoltInvoker(), 3000, dev, List.of(new BoltTarget(address, prefix + "dev"))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new GatewayClient(new RecordingBoltInvoker(), 3000, dev, List.of(new BoltTarget("127.0.0.1:1", "spiffe://im-chat/broker/wrong"))))
                .isInstanceOf(IllegalArgumentException.class);
        RecordingBoltInvoker invoker = new RecordingBoltInvoker();
        GatewayClient mtls = new GatewayClient(invoker, 3000, BoltTransportMode.MTLS, List.of(pair));
        mtls.closeConnections(new GatewayEndpointDTO("gw", "127.0.0.1", 12201, prefix + "certificate", Instant.now(), Instant.now()),
                new ConnectionCloseParams("close", 1L, "session"));
        assertThat(invoker.typedTarget.expectedPeerIdentity()).isEqualTo(prefix + "certificate");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mtls.closeConnections(new GatewayEndpointDTO("gw", "127.0.0.1", 12201, "trusted-network://127.0.0.1", Instant.now(), Instant.now()), new ConnectionCloseParams("close", 1L, "session")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("registered workload identity");
    }

    private static class RecordingBoltInvoker implements BoltInvoker {
        private String address;
        private String service;
        private String operation;
        private Object request;
        private Class<?> responseType;
        private Object response;

        private BoltTarget typedTarget;

        @Override
        @SuppressWarnings("unchecked")
        public <T, R> R invoke(BoltTarget target, String service, String operation,
                               T request, Class<R> responseType, int timeoutMillis) {
            this.typedTarget = target;

            String address = target.address();

            this.address = address;
            this.service = service;
            this.operation = operation;
            this.request = request;
            this.responseType = responseType;
            return (R) response;
        }

    }
}
