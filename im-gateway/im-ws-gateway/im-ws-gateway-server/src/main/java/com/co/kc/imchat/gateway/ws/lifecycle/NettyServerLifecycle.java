package com.co.kc.imchat.gateway.ws.lifecycle;

import com.co.kc.imchat.gateway.ws.server.NettyWebSocketServer;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Netty WebSocket 服务生命周期。
 * <p>
 * 交给 Spring 容器统一启动和停止 Netty Server，避免启动和销毁逻辑分散在不同 Bean 配置中。
 */
@Component
public class NettyServerLifecycle implements SmartLifecycle {
    private final NettyWebSocketServer server;
    private volatile boolean running;

    public NettyServerLifecycle(NettyWebSocketServer server) {
        this.server = server;
    }

    @Override
    public void start() {
        try {
            server.start();
            running = true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("failed to start im ws gateway netty server", ex);
        }
    }

    @Override
    public void stop() {
        server.stop();
        running = false;
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
