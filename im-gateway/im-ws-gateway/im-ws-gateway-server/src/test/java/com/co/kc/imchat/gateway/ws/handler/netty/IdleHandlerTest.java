package com.co.kc.imchat.gateway.ws.handler.netty;

import com.co.kc.imchat.gateway.ws.server.handler.IdleHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdleHandlerTest {

    @Test
    void closesChannelWhenReaderIdle() {
        EmbeddedChannel channel = new EmbeddedChannel(new IdleHandler());

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void keepsChannelOpenWhenWriterIdle() {
        EmbeddedChannel channel = new EmbeddedChannel(new IdleHandler());

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT);

        assertThat(channel.isOpen()).isTrue();
    }
}
