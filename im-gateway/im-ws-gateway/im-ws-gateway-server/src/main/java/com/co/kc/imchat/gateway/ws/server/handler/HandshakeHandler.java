package com.co.kc.imchat.gateway.ws.server.handler;

import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.common.constant.HttpHeaderConstants;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.gateway.ws.server.context.ContextAttributes;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.security.authentication.WsAuthenticationManager;
import com.co.kc.imchat.gateway.ws.security.identity.WsPrincipal;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * WS 网关握手处理器。
 * <p>
 * 负责校验客户端握手路径和 token，认证通过后向 Broker 注册连接，并把认证身份与连接 ID 写入 Channel 属性。
 */
@Slf4j
public class HandshakeHandler extends ChannelInboundHandlerAdapter {
    private final String gatewayId;
    private final String path;
    private final BrokerClient brokerClient;
    private final ConnectionRegistry connectionRegistry;
    private final WsAuthenticationManager authenticationManager;

    public HandshakeHandler(String gatewayId,
                            String path,
                            BrokerClient brokerClient,
                            ConnectionRegistry connectionRegistry,
                            WsAuthenticationManager authenticationManager) {
        this.gatewayId = gatewayId;
        this.path = path;
        this.brokerClient = brokerClient;
        this.connectionRegistry = connectionRegistry;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
        if (!(message instanceof FullHttpRequest request)) {
            super.channelRead(context, message);
            return;
        }
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        if (!path.equals(decoder.path())) {
            reject(context, request, HttpResponseStatus.NOT_FOUND);
            return;
        }
        authenticate(context, request, decoder);
    }

    private void authenticate(
            ChannelHandlerContext context,
            FullHttpRequest request,
            QueryStringDecoder decoder
    ) {
        try {
            authenticationManager.authenticate(token(request, decoder))
                    .whenComplete((principal, error) -> completeAuthentication(
                            context, request, principal, error));
        } catch (RuntimeException exception) {
            reject(context, request, HttpResponseStatus.UNAUTHORIZED);
        }
    }

    @SuppressWarnings("resource") // EventExecutor 的生命周期由 Netty Channel 管理。
    private void completeAuthentication(
            ChannelHandlerContext context,
            FullHttpRequest request,
            WsPrincipal principal,
            Throwable error
    ) {
        if (!context.executor().inEventLoop()) {
            context.executor().execute(() -> completeAuthentication(context, request, principal, error));
            return;
        }
        if (!context.channel().isActive()) {
            ReferenceCountUtil.release(request);
            return;
        }
        if (error != null || principal == null) {
            reject(context, request, HttpResponseStatus.UNAUTHORIZED);
            return;
        }
        registerAuthenticatedConnection(context, request, principal);
    }

    private void registerAuthenticatedConnection(
            ChannelHandlerContext context,
            FullHttpRequest request,
            WsPrincipal principal
    ) {
        String connectionId = UUID.randomUUID().toString();
        try {
            brokerClient.registerConnection(new ConnectionRegisterParams(principal.userId(), gatewayId));
        } catch (RuntimeException ex) {
            reject(context, request, HttpResponseStatus.SERVICE_UNAVAILABLE);
            return;
        }
        bindConnection(context, principal, connectionId);
        request.setUri(path);
        context.fireChannelRead(request);
    }

    private void bindConnection(
            ChannelHandlerContext context,
            WsPrincipal principal,
            String connectionId
    ) {
        context.channel().attr(ContextAttributes.PRINCIPAL).set(principal);
        context.channel().attr(ContextAttributes.SESSION_VERSION).set(principal.sessionVersion());
        context.channel().attr(ContextAttributes.CONNECTION_ID).set(connectionId);
        connectionRegistry.register(
                principal.userId(), principal.sessionVersion(), connectionId, context.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        WsPrincipal principal = context.channel().attr(ContextAttributes.PRINCIPAL).get();
        String connectionId = context.channel().attr(ContextAttributes.CONNECTION_ID).get();
        if (principal != null && connectionId != null) {
            unregisterConnection(principal, connectionId);
        }
        super.channelInactive(context);
    }

    private void unregisterConnection(WsPrincipal principal, String connectionId) {
        if (!connectionRegistry.unregister(connectionId)) {
            return;
        }
        try {
            brokerClient.unregisterConnection(
                    new ConnectionUnregisterParams(principal.userId(), gatewayId));
        } catch (RuntimeException exception) {
            log.warn("failed to unregister ws connection from broker, gatewayId:{}, connectionId:{}, error:{}",
                    gatewayId, connectionId, exception.toString());
        }
    }

    /**
     * 拒绝握手请求并关闭连接。
     */
    private void reject(ChannelHandlerContext context, Object message, HttpResponseStatus status) {
        ReferenceCountUtil.release(message);
        context.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status))
                .addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * 按查询参数、业务 token header、Authorization Bearer 的优先级提取握手令牌。
     */
    private String token(FullHttpRequest request, QueryStringDecoder decoder) {
        String token = stringParam(decoder, HttpHeaderConstants.TOKEN);
        if (token != null && !token.isBlank()) {
            return token;
        }
        token = request.headers().get(HttpHeaderConstants.TOKEN);
        if (token != null && !token.isBlank()) {
            return token;
        }
        String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(HttpHeaderConstants.BEARER_PREFIX)) {
            return authorization.substring(HttpHeaderConstants.BEARER_PREFIX.length()).trim();
        }
        return authorization;
    }

    private String stringParam(QueryStringDecoder decoder, String name) {
        return decoder.parameters()
                .getOrDefault(name, java.util.List.of())
                .stream()
                .findFirst()
                .orElse(null);
    }
}
