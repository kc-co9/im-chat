package com.co.kc.imchat.gateway.ws.sdk.client;

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
        GatewayClient adapter = new GatewayClient(invoker, 3000);
        GatewayEndpointDTO gateway = new GatewayEndpointDTO("gw-1", "10.0.0.8", 12201,
                Instant.now(), Instant.now());
        GatewayFrameWriteParams request = new GatewayFrameWriteParams(1L,
                new FrameResponse("1", FrameType.PUSH, "message.private.sent", "1", "trace",
                        null, null, Map.of()));

        GatewayFrameWriteResult response = adapter.writeFrame(gateway, request);

        assertThat(response.writeList()).containsExactly(GatewayFrameWriteDTO.accepted("conn-1"));
        assertThat(response.acceptedConnectionIds()).containsExactly("conn-1");
        assertThat(invoker.address).isEqualTo("10.0.0.8:12201");
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
        GatewayClient client = new GatewayClient(invoker, 3000);
        GatewayEndpointDTO gateway = new GatewayEndpointDTO(
                "gw-1", "10.0.0.8", 12201, Instant.now(), Instant.now());
        ConnectionCloseParams params = new ConnectionCloseParams(1L, "session-old");

        client.closeConnections(gateway, params);

        assertThat(invoker.service).isEqualTo(GatewayBoltService.CONNECTION.service());
        assertThat(invoker.operation).isEqualTo(GatewayBoltOperation.CLOSE_CONNECTIONS.operation());
        assertThat(invoker.request).isSameAs(params);
        assertThat(invoker.responseType).isEqualTo(Void.class);
    }

    private static class RecordingBoltInvoker implements BoltInvoker {
        private String address;
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
            this.service = service;
            this.operation = operation;
            this.request = request;
            this.responseType = responseType;
            return (R) response;
        }
    }
}
