package com.co.kc.imchat.gateway.ws.server.handler;

import com.co.kc.imchat.gateway.ws.server.context.ContextAttributes;
import com.co.kc.imchat.gateway.ws.security.identity.WsPrincipal;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * WS 连接空闲检测处理器。
 * <p>
 * 当连接超过配置的读空闲时间未收到客户端数据时，主动关闭连接，由断开流程完成本地和 Broker 路由清理。
 */
@Slf4j
public class IdleHandler extends ChannelDuplexHandler {

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof IdleStateEvent idleStateEvent
                && idleStateEvent.state() == IdleState.READER_IDLE) {
            String connectionId = context.channel().attr(ContextAttributes.CONNECTION_ID).get();
            WsPrincipal principal = context.channel().attr(ContextAttributes.PRINCIPAL).get();
            Long userId = principal == null ? null : principal.userId();
            log.info("close idle ws connection, userId:{}, connectionId:{}", userId, connectionId);
            context.close();
            return;
        }
        super.userEventTriggered(context, event);
    }
}
