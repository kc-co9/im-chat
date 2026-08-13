package com.co.kc.imchat.gateway.ws.server.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * 服务端 WebSocket 心跳发送器。
 * <p>
 * 浏览器不能主动发送协议级 ping；服务端定时发送 ping 后，浏览器会自动回复 pong，
 * 从而让读空闲检测只关闭真正失联的连接。
 */
public class HeartbeatHandler extends ChannelDuplexHandler {

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof IdleStateEvent idleStateEvent
                && idleStateEvent.state() == IdleState.WRITER_IDLE) {
            context.writeAndFlush(new PingWebSocketFrame());
            return;
        }
        super.userEventTriggered(context, event);
    }
}
