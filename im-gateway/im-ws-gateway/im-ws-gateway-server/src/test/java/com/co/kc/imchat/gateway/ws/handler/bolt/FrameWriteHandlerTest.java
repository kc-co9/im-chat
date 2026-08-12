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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FrameWriteHandlerTest {

    @Test
    void relaysFrameToRegisteredConnection() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry();
        EmbeddedChannel channel = new EmbeddedChannel();
        registry.register(1L, "conn-1", channel);
        FrameWriteHandler handler = new FrameWriteHandler(registry);
        GatewayFrameWriteParams params = new GatewayFrameWriteParams(1L,
                new FrameResponse("1", FrameType.PUSH, "message.private.sent", "1", "trace",
                        null, null, Map.of()));

        GatewayFrameWriteResult response = (GatewayFrameWriteResult) handler.handle(JsonUtils.toJson(params));

        assertThat(handler.service()).isEqualTo(GatewayBoltService.FRAME.service());
        assertThat(handler.operation()).isEqualTo(GatewayBoltOperation.WRITE_FRAME.operation());
        assertThat(response.writeList()).containsExactly(GatewayFrameWriteDTO.accepted("conn-1"));
        assertThat(response.acceptedConnectionIds()).containsExactly("conn-1");
        assertThat(response.failedConnectionIds()).isEmpty();
        TextWebSocketFrame outbound = channel.readOutbound();
        assertThat(JsonFrameCodec.decodeResponse(outbound.text()).cmd()).isEqualTo("message.private.sent");
    }
}
