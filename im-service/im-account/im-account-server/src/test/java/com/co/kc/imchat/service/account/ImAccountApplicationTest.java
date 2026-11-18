package com.co.kc.imchat.service.account;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.plugin.web.advice.ResultAdvice;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
import com.co.kc.imchat.plugin.session.token.codec.JwtTokenCodec;
import com.co.kc.imchat.service.account.infrastructure.mybatis.mapper.DbUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("startup-smoke")
@org.springframework.context.annotation.Import(ImAccountApplicationTest.DiscoveryFixture.class)
@org.springframework.test.context.ActiveProfiles("bolt-dev")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = ImAccountApplication.class, properties = {
        "server.port=0",
        "IM_BOLT_SYNTHETIC_DATA=true",
        "im.account.broker.bootstrap-targets[0].address=127.0.0.1:18020",
        "im.account.broker.bootstrap-targets[0].expected-peer-identity=spiffe://im-chat/broker/broker-local-1",
        "im.session.jwt.enabled=false",
        "im.dubbo.enabled=false",
        "dubbo.enabled=false",
        "jetcache.remote.default.type=mock",
        "im.datasource.sharding.enabled=false",
        "im.dubbo.registry.address=N/A",
        "spring.autoconfigure.exclude=org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class ImAccountApplicationTest {
    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private DbUserMapper dbUserMapper;

    @MockitoBean
    private JwtTokenCodec jwtTokenCodec;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private BoltInvoker boltInvoker;

    @Autowired
    private BrokerClient brokerClient;

    @MockitoBean
    private DistributedLockTemplate distributedLockTemplate;

    @MockitoBean
    private AfterTransactionCommitTemplate afterTransactionCommitTemplate;

    @Test
    void contextLoads() {
        org.assertj.core.api.Assertions.assertThat(brokerClient).isNotNull();
        org.assertj.core.api.Assertions.assertThat(boltInvoker).isInstanceOf(com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker.class);
        com.co.kc.imchat.plugin.bolt.client.BoltControlledClient client = applicationContext.getBean(com.co.kc.imchat.plugin.bolt.client.BoltControlledClient.class);
        org.assertj.core.api.Assertions.assertThat(client.isRunningControlledAssembly()).isTrue();
        org.assertj.core.api.Assertions.assertThat(client.configuredMode()).isEqualTo(BoltTransportMode.DEVELOPMENT_PLAINTEXT);
        org.assertj.core.api.Assertions.assertThat(applicationContext.getEnvironment().getActiveProfiles()).contains("bolt-dev");
    }

    @Test
    void loadsHttpResultAdviceFromImWebAutoConfiguration() {
        applicationContext.getBean(ResultAdvice.class);
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
                            "broker-local-1", com.co.kc.imchat.common.model.enums.ServiceName.IM_BROKER.value(),
                            "127.0.0.1", 18020, false)));
            return discovery;
        }
    }
}
