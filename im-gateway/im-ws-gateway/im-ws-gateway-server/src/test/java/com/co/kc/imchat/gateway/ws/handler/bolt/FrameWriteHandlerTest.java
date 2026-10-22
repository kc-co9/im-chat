package com.co.kc.imchat.gateway.ws.handler.bolt;

import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.ws.handler.FrameWriteHandler;
import com.co.kc.imchat.gateway.ws.sdk.model.dto.GatewayFrameWriteDTO;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltOperation;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltService;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.protocol.JsonFrameCodec;
import com.co.kc.imchat.gateway.ws.delivery.FrameDeliveryDeduplicator;
import com.co.kc.imchat.gateway.ws.security.GatewayBrokerAuthorizer;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameWriteHandlerTest {

    @Test
    void rejectsFrameWithoutTransportContext() {
        FrameWriteHandler handler = new FrameWriteHandler(new ConnectionRegistry(),
                new FrameDeliveryDeduplicator(
                        10, Duration.ofMinutes(1), System::currentTimeMillis),
                trustedNetworkAuthorizer());

        assertThatThrownBy(() -> handler.handle(null, "{}"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void relaysFrameToRegisteredConnection() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry();
        EmbeddedChannel channel = new EmbeddedChannel();
        registry.register(1L, "session-1", "conn-1", channel);
        FrameWriteHandler handler = new FrameWriteHandler(registry,
                new FrameDeliveryDeduplicator(
                        10, Duration.ofMinutes(1), System::currentTimeMillis),
                trustedNetworkAuthorizer());
        GatewayFrameWriteParams params = new GatewayFrameWriteParams(
                1L, "delivery-1", "attempt-1",
                new FrameResponse("1", FrameType.PUSH, "message.private.sent", "1", "trace",
                        null, null, Map.of()));

        GatewayFrameWriteResult response = (GatewayFrameWriteResult) handler.handle(
                requestContext(), JsonUtils.toJson(params));

        assertThat(handler.service()).isEqualTo(GatewayBoltService.FRAME.service());
        assertThat(handler.operation()).isEqualTo(GatewayBoltOperation.WRITE_FRAME.operation());
        assertThat(response.writeList()).containsExactly(GatewayFrameWriteDTO.accepted("conn-1"));
        assertThat(response.acceptedConnectionIds()).containsExactly("conn-1");
        assertThat(response.failedConnectionIds()).isEmpty();
        TextWebSocketFrame outbound = channel.readOutbound();
        assertThat(JsonFrameCodec.decodeResponse(outbound.text()).cmd()).isEqualTo("message.private.sent");

        GatewayFrameWriteResult duplicate = (GatewayFrameWriteResult) handler.handle(
                requestContext(), JsonUtils.toJson(new GatewayFrameWriteParams(
                        1L, "delivery-1", "attempt-2", params.frame())));

        assertThat(duplicate.acceptedConnectionIds()).containsExactly("conn-1");
        assertThat((Object) channel.readOutbound()).isNull();
    }

    private GatewayBrokerAuthorizer trustedNetworkAuthorizer() {
        return new GatewayBrokerAuthorizer(() -> {
            throw new AssertionError("trusted network fallback must not load cluster state");
        }, "spiffe://im-chat/broker/", true);
    }

    private BoltRequestContext requestContext() {
        return new BoltRequestContext("127.0.0.1:18020", "127.0.0.1", null, false, Set.of());
    }
}
