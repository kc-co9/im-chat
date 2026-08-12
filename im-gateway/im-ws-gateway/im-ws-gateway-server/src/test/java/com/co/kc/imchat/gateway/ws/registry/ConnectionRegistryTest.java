package com.co.kc.imchat.gateway.ws.registry;

import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.gateway.ws.sdk.model.dto.GatewayFrameWriteDTO;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionRegistryTest {

    @Test
    void closesAllActiveConnections() {
        ConnectionRegistry registry = new ConnectionRegistry();
        EmbeddedChannel first = new EmbeddedChannel();
        EmbeddedChannel second = new EmbeddedChannel();
        registry.register(1L, "conn-1", first);
        registry.register(2L, "conn-2", second);

        registry.closeAll();

        assertThat(first.isOpen()).isFalse();
        assertThat(second.isOpen()).isFalse();
        assertThat(registry.activeConnectionIds()).isEmpty();
    }

    @Test
    void pushReturnsFailedConnectionsWhenNettyWriteFails() {
        ConnectionRegistry registry = new ConnectionRegistry();
        EmbeddedChannel successChannel = new EmbeddedChannel();
        EmbeddedChannel failedChannel = new EmbeddedChannel(new FailingWriteHandler());
        registry.register(1L, "conn-1", successChannel);
        registry.register(1L, "conn-2", failedChannel);
        FrameResponse frame = new FrameResponse("1", FrameType.PUSH, "message.private.sent",
                null, "trace", null, null, Map.of("eventId", "event-1", "receiptId", "receipt-1"));

        List<GatewayFrameWriteDTO> result = registry.writeFrame(1L, frame);

        assertThat(result).containsExactlyInAnyOrder(
                GatewayFrameWriteDTO.accepted("conn-1"),
                GatewayFrameWriteDTO.failed("conn-2"));
        GatewayFrameWriteResult writeResult = new GatewayFrameWriteResult(result);
        assertThat(writeResult.acceptedConnectionIds()).containsExactly("conn-1");
        assertThat(writeResult.failedConnectionIds()).containsExactly("conn-2");
        assertThat(registry.activeConnectionIds()).containsExactly("conn-1");
    }

    @Test
    void cleansConnectionWhenSubmittedWriteFailsLater() {
        ConnectionRegistry registry = new ConnectionRegistry();
        AsyncFailingWriteHandler failingWriteHandler = new AsyncFailingWriteHandler();
        EmbeddedChannel channel = new EmbeddedChannel(failingWriteHandler);
        registry.register(1L, "conn-1", channel);
        FrameResponse frame = new FrameResponse("1", FrameType.PUSH, "message.private.sent",
                null, "trace", null, null, Map.of("eventId", "event-1", "receiptId", "receipt-1"));

        List<GatewayFrameWriteDTO> result = registry.writeFrame(1L, frame);
        failingWriteHandler.fail();

        assertThat(result).containsExactly(GatewayFrameWriteDTO.accepted("conn-1"));
        GatewayFrameWriteResult writeResult = new GatewayFrameWriteResult(result);
        assertThat(writeResult.acceptedConnectionIds()).containsExactly("conn-1");
        assertThat(writeResult.failedConnectionIds()).isEmpty();
        assertThat(registry.activeConnectionIds()).isEmpty();
        assertThat(registry.activeUserIds()).isEmpty();
        assertThat(channel.isOpen()).isFalse();
    }

    private static class FailingWriteHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
            ReferenceCountUtil.release(message);
            promise.setFailure(new IOException("write failed"));
        }
    }

    private static class AsyncFailingWriteHandler extends ChannelOutboundHandlerAdapter {
        private ChannelPromise promise;

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
            ReferenceCountUtil.release(message);
            this.promise = promise;
        }

        private void fail() {
            promise.setFailure(new IOException("write failed later"));
        }
    }
}
