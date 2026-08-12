package com.co.kc.imchat.gateway.ws.server.handler;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.common.constant.FrameCommand;
import com.co.kc.imchat.common.constant.FrameErrorCode;
import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.gateway.ws.server.context.ContextAttributes;
import com.co.kc.imchat.gateway.ws.protocol.JsonFrameCodec;
import com.co.kc.imchat.gateway.ws.security.identity.WsPrincipal;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * WS 网关业务帧处理器。
 * <p>
 * 负责把客户端文本帧解码为 IM 协议帧，完成网关侧轻量校验后交给 Broker 处理，并把响应帧写回客户端。
 */
public class FrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final BrokerClient brokerClient;

    public FrameHandler(BrokerClient brokerClient) {
        this.brokerClient = brokerClient;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, TextWebSocketFrame frame) {
        WsPrincipal principal = context.channel().attr(ContextAttributes.PRINCIPAL).get();
        String connectionId = context.channel().attr(ContextAttributes.CONNECTION_ID).get();
        FrameRequest request = null;
        FrameResponse response;
        try {
            request = JsonFrameCodec.decodeRequest(frame.text());
            response = handle(principal == null ? null : principal.userId(), connectionId, request);
        } catch (IllegalArgumentException ex) {
            response = FrameResponse.error(null, FrameErrorCode.BAD_FRAME);
        } catch (RuntimeException ex) {
            response = FrameResponse.error(request, FrameErrorCode.BROKER_UNAVAILABLE);
        }
        context.writeAndFlush(new TextWebSocketFrame(JsonFrameCodec.encodeResponse(response)));
    }

    private FrameResponse handle(Long userId, String connectionId, FrameRequest request) {
        if (request == null) {
            return FrameResponse.error(null, FrameErrorCode.BAD_FRAME, "上行帧不能为空");
        }
        if (userId == null || connectionId == null || connectionId.isBlank()) {
            return FrameResponse.error(request, FrameErrorCode.UNAUTHORIZED);
        }
        if (FrameCommand.from(request.cmd()).isEmpty()) {
            return FrameResponse.error(request, FrameErrorCode.UNKNOWN_CMD);
        }
        BrokerFrameWriteResult processed = brokerClient.writeFrame(BrokerFrameWriteParams.inbound(userId, connectionId, request));
        if (!processed.processed()) {
            return FrameResponse.error(request, processed.code(), processed.message());
        }
        return FrameResponse.ok(request);
    }
}
