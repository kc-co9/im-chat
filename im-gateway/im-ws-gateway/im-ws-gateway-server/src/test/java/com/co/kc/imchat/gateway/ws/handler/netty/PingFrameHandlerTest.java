package com.co.kc.imchat.gateway.ws.handler.netty;

import com.co.kc.imchat.gateway.ws.server.handler.PingFrameHandler;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PingFrameHandlerTest {

    @Test
    void repliesPongWithSamePayload() {
        EmbeddedChannel channel = new EmbeddedChannel(new PingFrameHandler());

        channel.writeInbound(new PingWebSocketFrame(Unpooled.copiedBuffer(new byte[]{1, 2, 3})));

        PongWebSocketFrame pong = channel.readOutbound();
        assertThat(pong.content().readableBytes()).isEqualTo(3);
        assertThat(pong.content().readByte()).isEqualTo((byte) 1);
        assertThat(pong.content().readByte()).isEqualTo((byte) 2);
        assertThat(pong.content().readByte()).isEqualTo((byte) 3);
        pong.release();
    }
}
