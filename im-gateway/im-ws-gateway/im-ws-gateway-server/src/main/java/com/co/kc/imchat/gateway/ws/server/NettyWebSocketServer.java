package com.co.kc.imchat.gateway.ws.server;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.model.params.GatewayUnregisterParams;
import com.co.kc.imchat.gateway.ws.server.handler.FrameHandler;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.server.handler.IdleHandler;
import com.co.kc.imchat.gateway.ws.server.handler.PingFrameHandler;
import com.co.kc.imchat.gateway.ws.server.handler.HandshakeHandler;
import com.co.kc.imchat.gateway.ws.security.authentication.WsAuthenticationManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.Channel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Netty 的 WebSocket 网关服务器。
 * <p>
 * 负责启动 WS 监听端口、组装握手鉴权/心跳/帧转发处理链，并在停止时清理本机连接和 Broker 路由。
 */
@Slf4j
public class NettyWebSocketServer {
    private final int port;
    private final String gatewayId;
    private final String path;
    private final BrokerClient brokerClient;
    private final ConnectionRegistry connectionRegistry;
    private final WsAuthenticationManager authenticationManager;
    private final int readerIdleSeconds;
    private final int maxFramePayloadLength;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyWebSocketServer(int port,
                                String gatewayId,
                                String path,
                                BrokerClient brokerClient,
                                ConnectionRegistry connectionRegistry,
                                WsAuthenticationManager authenticationManager,
                                int readerIdleSeconds,
                                int maxFramePayloadLength) {
        this.port = port;
        this.gatewayId = gatewayId;
        this.path = path;
        this.brokerClient = brokerClient;
        this.connectionRegistry = connectionRegistry;
        this.authenticationManager = authenticationManager;
        this.readerIdleSeconds = readerIdleSeconds;
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    public ChannelFuture start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            /*
                             * Pipeline 顺序按连接生命周期组织：
                             * 1. IdleStateHandler：先放入空闲检测，让后续任意阶段的长时间无读连接都能被关闭。
                             * 2. HttpServerCodec + HttpObjectAggregator：把 HTTP 握手请求解码并聚合为 FullHttpRequest。
                             * 3. HandshakeHandler：在协议升级前完成路径校验、token 认证、Broker 连接注册。
                             * 4. WebSocketServerProtocolHandler：完成标准 WebSocket 协议升级、帧编解码和 payload 大小限制。
                             *    TCP 粘包/拆包由 HTTP/WebSocket 编解码器处理，业务 handler 只接收完整 TextWebSocketFrame。
                             * 5. ConnectionIdleHandler：响应读空闲事件，关闭连接并触发后续连接清理。
                             * 6. PingFrameHandler：处理 ping/pong 心跳帧，不进入业务转发链路。
                             * 7. FrameHandler：处理文本业务帧，解码为 FrameRequest 后转交应用层分发。
                             */
                            channel.pipeline()
                                    .addLast(new IdleStateHandler(readerIdleSeconds, 0, 0, TimeUnit.SECONDS))
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(64 * 1024))
                                    .addLast(new HandshakeHandler(
                                            gatewayId, path, brokerClient, connectionRegistry,
                                            authenticationManager))
                                    .addLast(new WebSocketServerProtocolHandler(path, null, true, maxFramePayloadLength))
                                    .addLast(new IdleHandler())
                                    .addLast(new PingFrameHandler())
                                    .addLast(new FrameHandler(brokerClient));
                        }
                    });
            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            log.info("im ws gateway netty server started on port {}", port);
            return future;
        } catch (Throwable ex) {
            shutdownEventLoopGroups();
            throw startFailure(ex);
        }
    }

    /**
     * 停止 Netty 服务并清理 Broker 中属于当前网关的连接。
     */
    public void stop() {
        try {
            brokerClient.unregisterGateway(new GatewayUnregisterParams(gatewayId));
        } catch (RuntimeException ex) {
            log.warn("failed to unregister ws gateway from broker, gatewayId:{}", gatewayId, ex);
        }
        connectionRegistry.closeAll();
        if (serverChannel != null) {
            serverChannel.close();
        }
        shutdownEventLoopGroups();
    }

    private void shutdownEventLoopGroups() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    private RuntimeException startFailure(Throwable ex) throws InterruptedException {
        return switch (ex) {
            case InterruptedException interruptedException -> throw interruptedException;
            case RuntimeException runtimeException -> runtimeException;
            case Error error -> throw error;
            default -> new IllegalStateException("failed to start im ws gateway netty server", ex);
        };
    }
}
