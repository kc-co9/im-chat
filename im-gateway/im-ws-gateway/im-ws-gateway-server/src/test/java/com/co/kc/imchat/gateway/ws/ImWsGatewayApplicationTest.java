package com.co.kc.imchat.gateway.ws;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.gateway.ws.handler.FrameWriteHandler;
import com.co.kc.imchat.gateway.ws.server.NettyWebSocketServer;
import com.co.kc.imchat.gateway.ws.security.authentication.WsAuthenticationManager;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 正式 Gateway/受控 Bolt/注册生命周期装配；Account远端认证与Nacos为外部替换边界。
 * Broker不可用时注册按正式有界重试处理，不伪造成功响应或路由。
 */
@Tag("startup-smoke")
@org.springframework.context.annotation.Import(ImWsGatewayApplicationTest.DiscoveryFixture.class)
@ActiveProfiles("bolt-dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = ImWsGatewayApplication.class, properties = {
        "IM_BOLT_SYNTHETIC_DATA=true", "im.bolt.server.host=127.0.0.1", "im.bolt.server.port=0",
        "im.gateway.ws.port=0", "im.gateway.ws.bind-host=127.0.0.1",
        "im.gateway.ws.broker.bootstrap-targets[0].address=127.0.0.1:12200",
        "im.gateway.ws.broker.bootstrap-targets[0].expected-peer-identity=spiffe://im-chat/broker/broker-test",
        "im.dubbo.enabled=false", "dubbo.enabled=false", "jetcache.remote.default.type=mock",
        "spring.autoconfigure.exclude=org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
        "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class ImWsGatewayApplicationTest {
    @Autowired
    private FrameWriteHandler frameWriteHandler;
    @Autowired
    private BrokerClient brokerClient;
    @Autowired
    private BoltControlledClient client;
    @Autowired
    private NettyWebSocketServer server;
    @org.springframework.test.context.bean.override.convention.TestBean(methodName = "externalAccountAuthentication")
    private WsAuthenticationManager authenticationManager;

    static WsAuthenticationManager externalAccountAuthentication() {
        return new WsAuthenticationManager(org.mockito.Mockito.mock(com.co.kc.imchat.service.account.facade.AccountService.class),
                Runnable::run, java.time.Duration.ofSeconds(1));
    }

    @Test
    void contextLoadsWithRemoteBrokerClient() {
        assertThat(frameWriteHandler).isNotNull();
        assertThat(brokerClient).isNotNull();
        assertThat(server).isNotNull();
        assertThat(client.isRunningControlledAssembly()).isTrue();
        assertThat(client.configuredMode()).isEqualTo(BoltTransportMode.DEVELOPMENT_PLAINTEXT);
    }

    /**
     * 外部发现候选在 Bean 返回之前准备，与正式 YAML 的 bootstrap 配对求交；不伪造 Broker RPC 成功。
     */
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class DiscoveryFixture {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        org.springframework.cloud.client.discovery.DiscoveryClient startupDiscoveryClient() {
            org.springframework.cloud.client.discovery.DiscoveryClient discovery = org.mockito.Mockito.mock(
                    org.springframework.cloud.client.discovery.DiscoveryClient.class);
            org.mockito.Mockito.when(discovery.getInstances(com.co.kc.imchat.common.model.enums.ServiceName.IM_BROKER.value()))
                    .thenReturn(java.util.List.of(new org.springframework.cloud.client.DefaultServiceInstance(
                            "broker-test", com.co.kc.imchat.common.model.enums.ServiceName.IM_BROKER.value(),
                            "127.0.0.1", 12200, false)));
            return discovery;
        }
    }
}
