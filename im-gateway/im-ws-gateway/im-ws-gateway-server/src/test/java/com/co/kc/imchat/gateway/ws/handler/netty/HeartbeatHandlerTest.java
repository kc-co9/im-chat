package com.co.kc.imchat.gateway.ws.handler.netty;

import com.co.kc.imchat.gateway.ws.server.handler.HeartbeatHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeartbeatHandlerTest {

    @Test
    void sendsProtocolPingWhenConnectionIsWriteIdle() {
        EmbeddedChannel channel = new EmbeddedChannel(new HeartbeatHandler());

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT);

        Object outbound = channel.readOutbound();
        assertThat(outbound).isInstanceOf(PingWebSocketFrame.class);
        ((PingWebSocketFrame) outbound).release();
        channel.finishAndReleaseAll();
    }
}
