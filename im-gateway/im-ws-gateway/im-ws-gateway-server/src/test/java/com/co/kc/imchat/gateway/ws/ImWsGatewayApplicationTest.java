package com.co.kc.imchat.gateway.ws;

import com.co.kc.imchat.gateway.ws.handler.FrameWriteHandler;
import com.co.kc.imchat.gateway.ws.server.NettyWebSocketServer;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import io.netty.channel.ChannelFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("startup-smoke")
@SpringBootTest(classes = ImWsGatewayApplication.class, properties = {
        "im.gateway.ws.port=0",
        "im.bolt.client.enabled=false",
        "im.bolt.server.enabled=false",
        "im.dubbo.enabled=false",
        "dubbo.enabled=false",
        "spring.cloud.discovery.client.simple.instances.im-broker[0].uri=http://127.0.0.1:12200",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@Import(ImWsGatewayApplicationTest.TestBeans.class)
class ImWsGatewayApplicationTest {

    @Autowired
    private FrameWriteHandler frameWriteHandler;

    @Test
    void contextLoadsWithRemoteBrokerClient() {
        assertThat(frameWriteHandler).isNotNull();
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        BoltInvoker boltInvoker() {
            return new BoltInvoker() {
                @Override
                public <T, R> R invoke(String address, String service, String operation, T request,
                                       Class<R> responseType, int timeoutMillis) {
                    return null;
                }
            };
        }

        @Bean
        @Primary
        NettyWebSocketServer nettyWebSocketServer() {
            return new NoopNettyWebSocketServer();
        }
    }

    private static class NoopNettyWebSocketServer extends NettyWebSocketServer {

        private NoopNettyWebSocketServer() {
            super(0, "test-gateway", "/ws", null, null, null, 60, 65536);
        }

        @Override
        public ChannelFuture start() {
            return null;
        }

        @Override
        public void stop() {
        }
    }
}
