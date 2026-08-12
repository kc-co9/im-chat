package com.co.kc.imchat.gateway.ws.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;

/**
 * WebSocket Ping 帧处理器。
 * <p>
 * 收到客户端 ping 后原样保留载荷并回复 pong，用于维持浏览器和网关之间的心跳。
 */
public class PingFrameHandler extends SimpleChannelInboundHandler<PingWebSocketFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext context, PingWebSocketFrame frame) {
        context.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
    }
}
