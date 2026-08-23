package com.co.kc.imchat.gateway.ws.handler;

import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionSyncParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.common.constant.FrameErrorCode;
import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.ws.server.context.ContextAttributes;
import com.co.kc.imchat.gateway.ws.support.BrokerClientTestSupport;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.gateway.ws.server.handler.FrameHandler;
import com.co.kc.imchat.gateway.ws.protocol.JsonFrameCodec;
import com.co.kc.imchat.gateway.ws.security.identity.WsPrincipal;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FrameHandlerTest {

    @Test
    void handlesTextFrameAndWritesResponse() {
        EmbeddedChannel channel = new EmbeddedChannel(new FrameHandler(new SuccessfulBrokerClient()));
        channel.attr(ContextAttributes.PRINCIPAL).set(new WsPrincipal(1L, "session-1"));
        channel.attr(ContextAttributes.CONNECTION_ID).set("conn-1");
        FrameRequest request = new FrameRequest("1", "message.private.send", "10001",
                "trace", Map.of("userId", 1L));

        channel.writeInbound(new TextWebSocketFrame(JsonUtils.toJson(request)));

        TextWebSocketFrame response = channel.readOutbound();
        FrameResponse responseFrame = JsonFrameCodec.decodeResponse(response.text());
        assertEquals(FrameType.RESPONSE, responseFrame.type());
        assertEquals("message.private.send", responseFrame.cmd());
    }

    @Test
    void returnsBrokerUnavailableErrorWhenRouteFails() {
        EmbeddedChannel channel = new EmbeddedChannel(new FrameHandler(new FailingBrokerClient()));
        channel.attr(ContextAttributes.PRINCIPAL).set(new WsPrincipal(1L, "session-1"));
        channel.attr(ContextAttributes.CONNECTION_ID).set("conn-1");
        FrameRequest request = new FrameRequest("1", "message.private.send", "10001",
                "trace", Map.of("userId", 1L));

        channel.writeInbound(new TextWebSocketFrame(JsonUtils.toJson(request)));

        TextWebSocketFrame response = channel.readOutbound();
        FrameResponse responseFrame = JsonFrameCodec.decodeResponse(response.text());
        assertEquals(FrameType.ERROR, responseFrame.type());
        assertEquals("message.private.send", responseFrame.cmd());
        assertEquals("10001", responseFrame.seq());
        assertEquals("trace", responseFrame.traceId());
        assertEquals(FrameErrorCode.BROKER_UNAVAILABLE.code(), responseFrame.body().get("code"));
    }

    @Test
    void rejectsUnknownCommandWithoutRoutingToBroker() {
        ProbeBrokerClient brokerClient = new ProbeBrokerClient();
        EmbeddedChannel channel = new EmbeddedChannel(new FrameHandler(brokerClient));
        channel.attr(ContextAttributes.PRINCIPAL).set(new WsPrincipal(1L, "session-1"));
        channel.attr(ContextAttributes.CONNECTION_ID).set("conn-1");
        FrameRequest request = new FrameRequest("1", "unknown.command", "10001", "trace", Map.of());

        channel.writeInbound(new TextWebSocketFrame(JsonUtils.toJson(request)));

        TextWebSocketFrame response = channel.readOutbound();
        FrameResponse responseFrame = JsonFrameCodec.decodeResponse(response.text());
        assertEquals(FrameType.ERROR, responseFrame.type());
        assertEquals(FrameErrorCode.UNKNOWN_CMD.code(), responseFrame.body().get("code"));
        assertFalse(brokerClient.handled);
    }

    @Test
    void rejectsMissingConnectionContextWithoutRoutingToBroker() {
        ProbeBrokerClient brokerClient = new ProbeBrokerClient();
        EmbeddedChannel channel = new EmbeddedChannel(new FrameHandler(brokerClient));
        FrameRequest request = new FrameRequest("1", "message.private.send", "10001", "trace", Map.of());

        channel.writeInbound(new TextWebSocketFrame(JsonUtils.toJson(request)));

        TextWebSocketFrame response = channel.readOutbound();
        FrameResponse responseFrame = JsonFrameCodec.decodeResponse(response.text());
        assertEquals(FrameType.ERROR, responseFrame.type());
        assertEquals(FrameErrorCode.UNAUTHORIZED.code(), responseFrame.body().get("code"));
        assertFalse(brokerClient.handled);
    }

    private static class SuccessfulBrokerClient extends BrokerClient {
        private SuccessfulBrokerClient() {
            super(BrokerClientTestSupport.invoker(), BrokerClientTestSupport.discovery(),
                    ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000);
        }

        @Override
        public void registerGateway(GatewayRegisterParams command) {
        }

        @Override
        public void syncConnections(ConnectionSyncParams command) {
        }

        @Override
        public void registerConnection(ConnectionRegisterParams command) {
        }

        @Override
        public void unregisterConnection(ConnectionUnregisterParams command) {
        }

        @Override
        public BrokerFrameWriteResult writeFrame(BrokerFrameWriteParams command) {
            return BrokerFrameWriteResult.ok();
        }
    }

    private static class FailingBrokerClient extends SuccessfulBrokerClient {
        @Override
        public BrokerFrameWriteResult writeFrame(BrokerFrameWriteParams command) {
            throw new IllegalStateException("broker unavailable");
        }
    }

    private static class ProbeBrokerClient extends SuccessfulBrokerClient {
        private boolean handled;

        @Override
        public BrokerFrameWriteResult writeFrame(BrokerFrameWriteParams command) {
            handled = true;
            return super.writeFrame(command);
        }
    }
}
