package com.co.kc.imchat.gateway.ws.server.handler.fixture;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class WebSocketProtocolWrite {
    public void writeWebSocket(ChannelHandlerContext context, String text) {
        context.writeAndFlush(new TextWebSocketFrame(text));
    }
}
